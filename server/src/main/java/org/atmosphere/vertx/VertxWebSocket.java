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

import org.atmosphere.cpr.AtmosphereConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.http.ServerWebSocket;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An Atmosphere's {@link org.atmosphere.websocket.WebSocket} wrapper around a {@link ServerWebSocket}
 *
 * @author Jeanfrancois Arcand
 */
public class VertxWebSocket extends org.atmosphere.websocket.WebSocket {
    private static final Logger logger = LoggerFactory.getLogger(VertxWebSocket.class);
    private final AtomicBoolean isOpen = new AtomicBoolean(true);
    private final ServerWebSocket webSocket;

    public VertxWebSocket(final AtmosphereConfig config, final ServerWebSocket webSocket) {
        super(config);
        this.webSocket = webSocket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(String data) throws IOException {
        logger.trace("WebSocket.write()");
        webSocket.writeTextFrame(data);
        lastWrite = System.currentTimeMillis();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(byte[] data, int offset, int length) throws IOException {
        webSocket.writeTextFrame(new String(data, offset, length, "UTF-8"));
        return this;
    }

    /**
     * {@inheritDoc}
     */
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
            webSocket.close();
        }
    }
}


