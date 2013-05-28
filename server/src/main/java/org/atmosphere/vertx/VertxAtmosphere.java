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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.ServerWebSocket;

public class VertxAtmosphere {
    private static final Logger logger = LoggerFactory.getLogger(VertxAtmosphere.class);

    private final Builder b;

    private VertxAtmosphere(Builder b) {
        this.b = b;

        RouteMatcher routeMatcher = new RouteMatcher();
        routeMatcher.get(b.url, handleHttp());
        routeMatcher.post(b.url, handleHttp());
        routeMatcher.noMatch(b.httpServer.requestHandler());

        b.httpServer.requestHandler(routeMatcher);
        b.httpServer.websocketHandler(handleWebSocket());

        if (b.resource != null) {
            AtmosphereCoordinator.instance().discover(b.resource);
        }
        AtmosphereCoordinator.instance().ready();
    }

    public final static class Builder {

        private String url;
        private HttpServer httpServer;
        private Class<?> resource;
        private AtmosphereCoordinator coordinator;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder httpServer(HttpServer httpServer) {
            this.httpServer = httpServer;
            return this;
        }

        public VertxAtmosphere build() {
            coordinator = AtmosphereCoordinator.instance();
            return new VertxAtmosphere(this);
        }

        public Builder resource(Class<?> resource) {
            this.resource = resource;
            return this;
        }
    }

    private Handler<HttpServerRequest> handleHttp() {

        return new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest req) {
                logger.trace("HTTP received");
                new VertxAsyncIOWriter(req);
                b.coordinator.route(req);
            }
        };
    }

    private Handler<ServerWebSocket> handleWebSocket() {
        return new Handler<ServerWebSocket>() {
            @Override
            public void handle(ServerWebSocket webSocket) {
                logger.trace("WebSocket received {}", webSocket);
                b.coordinator.route(webSocket);
            }
        };
    }
}
