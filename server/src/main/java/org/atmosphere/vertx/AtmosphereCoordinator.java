/*
 * Copyright 2013 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.vertx;

import org.atmosphere.container.NettyCometSupport;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AsynchronousProcessor;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.util.EndpointMapper;
import org.atmosphere.util.ExecutorsFactory;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.ServerWebSocket;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;

/**
 * This class control the {@link AtmosphereFramework} life cycle.
 *
 * @author Jean-Francois Arcand
 */
public class AtmosphereCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(AtmosphereCoordinator.class);

    private final AtmosphereFramework framework;
    public final static AtmosphereCoordinator instance = new AtmosphereCoordinator();
    private final ScheduledExecutorService suspendTimer;
    private final EndpointMapper<AtmosphereFramework.AtmosphereHandlerWrapper> mapper;
    private final WebSocketProcessor webSocketProcessor;

    private AtmosphereCoordinator() {
        framework = new AtmosphereFramework();
        framework.setAsyncSupport(new NettyCometSupport(framework().getAtmosphereConfig()));
        suspendTimer = ExecutorsFactory.getScheduler(framework.getAtmosphereConfig());
        mapper = framework.endPointMapper();
        webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(framework);
    }

    public AtmosphereCoordinator discover(Class<?> clazz) {
        framework.addAnnotationPackage(clazz);
        return this;
    }

    public AtmosphereCoordinator ready() {
        framework().addInitParameter(ApplicationConfig.ALLOW_QUERYSTRING_AS_REQUEST, "false");
        framework().init();
        return this;
    }

    public boolean matchPath(String path) {
        return mapper.map(path, framework().getAtmosphereHandlers()) == null ? false : true;
    }

    public AtmosphereCoordinator path(String mappingPath) {
        framework.addInitParameter(ApplicationConfig.ATMOSPHERE_HANDLER_MAPPING, mappingPath);
        return this;
    }

    public AtmosphereCoordinator shutdown() {
        framework.destroy();
        return this;
    }

    public static final AtmosphereCoordinator instance() {
        return instance;
    }

    public AtmosphereFramework framework() {
        return framework;
    }

    /**
     * Route the {@link ServerWebSocket} into the {@link AtmosphereFramework}
     *
     * @param webSocket the {@link ServerWebSocket}
     */
    public void route(ServerWebSocket webSocket) {
        Map<String, List<String>> paramMap = new QueryStringDecoder(webSocket.path.replaceFirst("@", "?")).getParameters();
        LinkedHashMap params = new LinkedHashMap<String, List<String>>(paramMap.size());
        for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
            params.put(entry.getKey(), entry.getValue().get(0));
        }

        if (params.size() == 0) {
            // TODO: vert.x trim the query string, unfortunately.
            params.put(HeaderConfig.X_ATMO_PROTOCOL, new String[]{"true"});
            params.put(HeaderConfig.X_ATMOSPHERE_FRAMEWORK, new String[]{"1.1"});
            params.put(HeaderConfig.X_ATMOSPHERE_TRACKING_ID, new String[]{"0"});
            params.put(HeaderConfig.X_ATMOSPHERE_TRANSPORT, new String[]{"websocket"});
        }

        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
        AtmosphereRequest r = requestBuilder
                .requestURI("/")
                .pathInfo(webSocket.path)
                .queryStrings(params)
                .build();

        final WebSocket w = new VertxWebSocket(AtmosphereCoordinator.instance().framework().getAtmosphereConfig(), webSocket);
        try {
            webSocketProcessor.open(w, r, AtmosphereResponse.newInstance(framework.getAtmosphereConfig(), r, w));
        } catch (IOException e) {
            logger.debug("", e);
        }

        webSocket.dataHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer data) {
                webSocketProcessor.invokeWebSocketProtocol(w, data.toString());
            }
        });
        webSocket.exceptionHandler(new Handler<Exception>() {
            @Override
            public void handle(Exception event) {
                w.close();
                logger.debug("", event);
                webSocketProcessor.close(w, 1006);
            }
        });
        webSocket.closedHandler(new SimpleHandler() {
            @Override
            protected void handle() {
                w.close();
                webSocketProcessor.close(w, 1005);
            }
        });
    }

    public boolean route(AtmosphereRequest request, AtmosphereResponse response) throws IOException {
        boolean resumeOnBroadcast = false;
        boolean keptOpen = true;
        boolean skipClose = false;
        final VertxAsyncIOWriter w = VertxAsyncIOWriter.class.cast(response.getAsyncIOWriter());

        try {

            Action a = framework.doCometSupport(request, response);
            final AsynchronousProcessor.AsynchronousProcessorHook hook = (AsynchronousProcessor.AsynchronousProcessorHook)
                    request.getAttribute(FrameworkConfig.ASYNCHRONOUS_HOOK);

            String transport = (String) request.getAttribute(FrameworkConfig.TRANSPORT_IN_USE);
            if (transport == null) {
                transport = request.getHeader(X_ATMOSPHERE_TRANSPORT);
            }

            if (a.type() == Action.TYPE.SUSPEND) {
                if (transport.equalsIgnoreCase(HeaderConfig.LONG_POLLING_TRANSPORT) || transport.equalsIgnoreCase(HeaderConfig.JSONP_TRANSPORT)) {
                    resumeOnBroadcast = true;
                }
            } else {
                keptOpen = false;
            }

            logger.debug("Transport {} resumeOnBroadcast {}", transport, resumeOnBroadcast);

            final Action action = (Action) request.getAttribute(NettyCometSupport.SUSPEND);
            if (action != null && action.type() == Action.TYPE.SUSPEND && action.timeout() != -1) {
                final AtomicReference<Future<?>> f = new AtomicReference();
                f.set(suspendTimer.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        if (!w.isClosed() && (System.currentTimeMillis() - w.lastTick()) > action.timeout()) {
                            hook.timedOut();
                            f.get().cancel(true);
                        }
                    }
                }, action.timeout(), action.timeout(), TimeUnit.MILLISECONDS));
            } else if (action != null && action.type() == Action.TYPE.RESUME) {
                resumeOnBroadcast = false;
            }
            w.resumeOnBroadcast(resumeOnBroadcast);
        } catch (Throwable e) {
            logger.error("Unable to process request", e);
            keptOpen = false;
        } finally {
            if (w != null && !resumeOnBroadcast && !keptOpen) {
                if (!skipClose) {
                    w.close((AtmosphereResponse) null);
                }
            }
        }
        return keptOpen;
    }

}
