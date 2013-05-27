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

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.cpr.WebSocketProcessorFactory;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class VertxWebSocket extends org.atmosphere.websocket.WebSocket {
    private static final Logger logger = LoggerFactory.getLogger(VertxWebSocket.class);

    private final AtomicBoolean isOpen = new AtomicBoolean(true);
    private final ServerWebSocket out;
    private final WebSocketProcessor webSocketProcessor;

    public VertxWebSocket(final AtmosphereConfig config, final ServerWebSocket webSocket) {
        super(config);

        webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(config.framework());

        this.out = webSocket;

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

        try {
            webSocketProcessor.open(this, r, AtmosphereResponse.newInstance(config, r, this));
        } catch (IOException e) {
            logger.debug("", e);
        }

        webSocket.dataHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer data) {
                webSocketProcessor.invokeWebSocketProtocol(VertxWebSocket.this, data.toString());
            }
        });
        webSocket.exceptionHandler(new Handler<Exception>() {
            @Override
            public void handle(Exception event) {
                isOpen.set(false);
                logger.debug("", event);
                webSocketProcessor.close(VertxWebSocket.this, 1006);
            }
        });
        webSocket.closedHandler(new SimpleHandler() {
            @Override
            protected void handle() {
                isOpen.set(false);
                webSocketProcessor.close(VertxWebSocket.this, 1005);
            }
        });

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(String data) throws IOException {
        logger.trace("WebSocket.write()");
        out.writeTextFrame(data);
        lastWrite = System.currentTimeMillis();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(byte[] data, int offset, int length) throws IOException {
        out.writeTextFrame(new String(data, offset, length, "UTF-8"));
        return this;
    }

    @Override
    public boolean isOpen() {
        return isOpen.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (!isOpen()) {
            isOpen.set(false);
            out.close();
        }
    }
}


