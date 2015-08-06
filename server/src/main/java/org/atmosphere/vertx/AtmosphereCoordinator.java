/*
 * Copyright 2015 Async-IO.org
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

import io.netty.handler.codec.http.QueryStringDecoder;
import org.atmosphere.container.NettyCometSupport;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AsynchronousProcessor;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.util.EndpointMapper;
import org.atmosphere.util.ExecutorsFactory;
import org.atmosphere.util.ServletProxyFactory;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Handler;
import io.vertx.core.VoidHandler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_FRAMEWORK;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRACKING_ID;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;
import static org.atmosphere.cpr.HeaderConfig.X_ATMO_PROTOCOL;

/**
 * This class control the {@link AtmosphereFramework} life cycle.
 *
 * @author Jean-Francois Arcand
 */
public class AtmosphereCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(AtmosphereCoordinator.class);

    private final AtmosphereFramework framework;
    private final ScheduledExecutorService suspendTimer;
    private final EndpointMapper<AtmosphereFramework.AtmosphereHandlerWrapper> mapper;
    private  WebSocketProcessor webSocketProcessor;
    private final AsynchronousProcessor asynchronousProcessor;

    AtmosphereCoordinator() {
        framework = new AtmosphereFramework();
        asynchronousProcessor = new NettyCometSupport(framework().getAtmosphereConfig());
        framework.setAsyncSupport(asynchronousProcessor);
        suspendTimer = ExecutorsFactory.getScheduler(framework.getAtmosphereConfig());
        mapper = framework.endPointMapper();
    }

    public AtmosphereCoordinator configure(final VertxAtmosphere.Builder b) {
        try {
            if (b.broadcasterFactory != null) {
                framework.setBroadcasterFactory(b.broadcasterFactory);
            }
        } catch (Throwable t) {
            logger.trace("", t);
        }

        if (b.broadcasterCache != null) {
            try {
                framework.setBroadcasterCacheClassName(b.broadcasterCache.getName());
            } catch (Throwable t) {
                logger.trace("", t);
            }
        }

        if (b.webSocketProtocol != null) {
            framework.setWebSocketProtocolClassName(b.webSocketProtocol.getName());
        }

        for (AtmosphereInterceptor i : b.interceptors) {
            framework.interceptor(i);
        }

        for (Map.Entry<String,String> e : b.initParams.entrySet()){
            framework.addInitParameter(e.getKey(),e.getValue());
        }

        ServletProxyFactory.getDefault().addMethodHandler("getServerInfo", new ServletProxyFactory.MethodHandler() {
            @Override
            public Object handle(Object clazz, Method method, Object[] methodObjects) {
                return "Vertosphere/1.0.0";
            }
        });

        discover(b.resource);

        return this;
    }

    public AtmosphereCoordinator discover(Class<?> clazz) {
        framework.addAnnotationPackage(clazz);
        return this;
    }

    public AtmosphereCoordinator ready() {
        framework().init();
        webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(framework);

        return this;
    }

    public boolean matchPath(String path) {
        try {
            return mapper.map(path, framework().getAtmosphereHandlers()) == null ? false : true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public AtmosphereCoordinator path(String mappingPath) {
        framework.addInitParameter(ApplicationConfig.ATMOSPHERE_HANDLER_MAPPING, mappingPath);
        return this;
    }

    public AtmosphereCoordinator shutdown() {
        framework.destroy();
        return this;
    }

    public AtmosphereFramework framework() {
        return framework;
    }

    /**
     * Route the {@link ServerWebSocket} into the {@link AtmosphereFramework}
     *
     * @param webSocket the {@link ServerWebSocket}
	 * @return the the {@link AtmosphereCoordinator}
     */

    public AtmosphereCoordinator route(ServerWebSocket webSocket) {
        Map<String, List<String>> paramMap = new QueryStringDecoder("?" + webSocket.query()).parameters();
        Map<String, String[]> params = new LinkedHashMap<String, String[]>(paramMap.size());
        for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
            params.put(entry.getKey(), entry.getValue().toArray(new String[]{}));
        }

        String contentType = "application/json";
        if (params.size() == 0) {
            // TODO: vert.x trim the query string, unfortunately.
            params.put(X_ATMO_PROTOCOL, new String[]{"true"});
            params.put(X_ATMOSPHERE_FRAMEWORK, new String[]{"2.1"});
            params.put(X_ATMOSPHERE_TRACKING_ID, new String[]{"0"});
            params.put(X_ATMOSPHERE_TRANSPORT, new String[]{"websocket"});
            params.put("Content-Type", new String[]{contentType});
        } else if (params.containsKey("Content-Type") && params.get("Content-Type").length > 0) {
            contentType = params.get("Content-Type")[0];
        }

        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
        AtmosphereRequest r = requestBuilder
                .requestURI(webSocket.path())
                .requestURL("http://0.0.0.0" + webSocket.path())
                .contentType(contentType)
                .pathInfo(webSocket.path())
                .queryStrings(params)
                .build();
        
        final WebSocket w = new VertxWebSocket(framework.getAtmosphereConfig(), webSocket);
        try {
            webSocketProcessor.open(w, r, AtmosphereResponse.newInstance(framework.getAtmosphereConfig(), r, w));
        } catch (IOException e) {
            logger.debug("", e);
        }

        webSocket.handler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer data) {
                webSocketProcessor.invokeWebSocketProtocol(w, data.toString());
            }
        });
        webSocket.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable event) {
                w.close();
                logger.debug("", event);
                webSocketProcessor.close(w, 1006);
            }
        });
        webSocket.closeHandler(new VoidHandler() {
            @Override
            protected void handle() {
                w.close();
                webSocketProcessor.close(w, 1005);
            }
        });
        return this;
    }

    public AtmosphereCoordinator route(AtmosphereRequest request, AtmosphereResponse response) throws IOException {
        final VertxAsyncIOWriter w = VertxAsyncIOWriter.class.cast(response.getAsyncIOWriter());
        try {

            Action a = framework.doCometSupport(request, response);
            final AtmosphereResourceImpl impl = (AtmosphereResourceImpl) request.getAttribute(FrameworkConfig.ATMOSPHERE_RESOURCE);

            String transport = (String) request.getAttribute(FrameworkConfig.TRANSPORT_IN_USE);
            if (transport == null) {
                transport = request.getHeader(X_ATMOSPHERE_TRANSPORT);
            }

            logger.debug("Transport {} action {}", transport, a);
            final Action action = (Action) request.getAttribute(NettyCometSupport.SUSPEND);
            if (action != null && action.type() == Action.TYPE.SUSPEND && action.timeout() != -1) {
                final AtomicReference<Future<?>> f = new AtomicReference();
                f.set(suspendTimer.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        if (!w.isClosed() && (System.currentTimeMillis() - w.lastTick()) > action.timeout()) {
                            asynchronousProcessor.endRequest(impl, false);
                            f.get().cancel(true);
                        }
                    }
                }, action.timeout(), action.timeout(), TimeUnit.MILLISECONDS));
            }
        } catch (Throwable e) {
            logger.error("Unable to process request", e);
        }
        return this;
    }

    /**
     * Route an http request inside the {@link AtmosphereFramework}
     *
     * @param request the {@link HttpServerRequest}
	 * @return the {@link AtmosphereCoordinator}
     */
    public AtmosphereCoordinator route(final HttpServerRequest request) {
        boolean async = false;
        try {
            VertxAsyncIOWriter w = new VertxAsyncIOWriter(request);
            final AtmosphereRequest r = AtmosphereUtils.request(request);
            final AtmosphereResponse res = new AtmosphereResponse.Builder()
                    .asyncIOWriter(w)
                    .writeHeader(false)
                    .request(r).build();

            request.response().exceptionHandler(new Handler<Throwable>() {
                @Override
                public void handle(Throwable event) {
                    try {
                        logger.debug("exceptionHandler", event);
                        AsynchronousProcessor.class.cast(framework.getAsyncSupport())
                                .cancelled(r, res);
                    } catch (IOException e) {
                        logger.debug("", e);
                    } catch (ServletException e) {
                        logger.debug("", e);
                    }
                }
            });

            if (r.getMethod().equalsIgnoreCase("POST")) {
                async = true;
                request.bodyHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer body) {
                        r.body(body.toString());
                        try {
                            route(r, res);
                            request.response().end();
                        } catch (IOException e1) {
                            logger.debug("", e1);
                        }
                    }
                });
            }

            if (!async) {
                route(r, res);
            }
        } catch (Throwable e) {
            logger.error("", e);
        }
        return this;
    }
}
