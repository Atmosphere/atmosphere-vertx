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

import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterCache;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.websocket.WebSocketProtocol;
import org.atmosphere.websocket.protocol.SimpleHttpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.ServerWebSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A boostrap class that can be used to bridge Atmosphere and Vert.x. As simple as
 * <pre><blockquote>
 public class VertxJerseyChat extends Verticle {

     private static final Logger logger = LoggerFactory.getLogger(VertxJerseyChat.class);

     public void start() throws Exception {
         VertxAtmosphere.Builder b = new VertxAtmosphere.Builder();
         HttpServer httpServer = vertx.createHttpServer();

         httpServer.requestHandler(new Handler&gt;HttpServerRequest&lt;() {
             public void handle(HttpServerRequest req) {
                 String path = req.path;
                 if (path.equals("/")) {
                     path = "/index.html";
                 }

                 logger.info("Servicing request {}", path);
                 req.response.sendFile("src/main/resources" + path);
             }
         });

         b.resource(ResourceChat.class)
          .initParam(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json")
          .httpServer(httpServer).url("/chat").build();

         httpServer.listen(8080);
     }
 }
 * </blockquote></pre>
 * @author  Jeanfrancois Arcand
 */
public class VertxAtmosphere {
    private static final Logger logger = LoggerFactory.getLogger(VertxAtmosphere.class);

    private final Builder b;
    private final AtmosphereCoordinator coordinator = new AtmosphereCoordinator();

    private VertxAtmosphere(Builder b) {
        this.b = b;

        RouteMatcher routeMatcher = new RouteMatcher();
        routeMatcher.get(b.url, handleHttp());
        routeMatcher.post(b.url, handleHttp());
        routeMatcher.noMatch(b.httpServer.requestHandler());

        b.httpServer.requestHandler(routeMatcher);
        b.httpServer.websocketHandler(handleWebSocket());

        if (b.resource != null) {
            coordinator.configure(b);
        }
        coordinator.ready();
    }

    /**
     * Is the path match one of the resource deployed.
     * @param path
     * @return boolean if true.
     */
    public boolean matchPath(String path) {
        return coordinator.matchPath(path);
    }

    public final static class Builder {

        protected String url;
        protected HttpServer httpServer;
        protected Class<?> resource;
        protected final Map<String, String> initParams = new HashMap<String, String>();
        protected Class<? extends WebSocketProtocol> webSocketProtocol = SimpleHttpProtocol.class;

        protected Class<Broadcaster> broadcasterClass;
        protected BroadcasterFactory broadcasterFactory;
        protected Class<? extends BroadcasterCache> broadcasterCache;
        protected final List<AtmosphereInterceptor> interceptors = new ArrayList<AtmosphereInterceptor>();

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Set the Vert.x {@link HttpServer}
         * @param httpServer a Vert.x {@link HttpServer}
         * @return this
         */
        public Builder httpServer(HttpServer httpServer) {
            this.httpServer = httpServer;
            return this;
        }

        /**
         * Create the associated {@link VertxAtmosphere}
         * @return a  {@link VertxAtmosphere}
         */
        public VertxAtmosphere build() {
            return new VertxAtmosphere(this);
        }

        /**
         * An annotated Atmosphere class. Supported annotation are {@link org.atmosphere.config.service.ManagedService},
         * {@link org.atmosphere.config.service.AtmosphereHandlerService}, {@link org.atmosphere.config.service.MeteorService},
         * {@link org.atmosphere.config.service.WebSocketHandlerService} and any Jersey resource.
         *
         * @param resource
         * @return this;
         */
        public Builder resource(Class<?> resource) {
            this.resource = resource;
            return this;
        }

        /**
         * Add some init param
         *
         * @param name  the name
         * @param value the value
         * @return this
         */
        public Builder initParam(String name, String value) {
            initParams.put(name, value);
            return this;
        }

        /**
         * Configure the default {@link Broadcaster}
         *
         * @param broadcasterClass a Broadcaster
         * @return this
         */
        public Builder broadcaster(Class<Broadcaster> broadcasterClass) {
            this.broadcasterClass = broadcasterClass;
            return this;
        }

        /**
         * Configure the default {@link BroadcasterFactory}
         *
         * @param broadcasterFactory a BroadcasterFactory's class
         * @return this
         */
        public Builder broadcasterFactory(BroadcasterFactory broadcasterFactory) {
            this.broadcasterFactory = broadcasterFactory;
            return this;
        }

        /**
         * Configure the default {@link BroadcasterCache}
         *
         * @param broadcasterCache a BroadcasterCache's class
         * @return this
         */
        public Builder broadcasterCache(Class<? extends BroadcasterCache> broadcasterCache) {
            this.broadcasterCache = broadcasterCache;
            return this;
        }

        /**
         * Configure the default {@link WebSocketProtocol}
         *
         * @param webSocketProtocol a WebSocketProtocol's class
         * @return this
         */
        public Builder webSocketProtocol(Class<? extends WebSocketProtocol> webSocketProtocol) {
            this.webSocketProtocol = webSocketProtocol;
            return this;
        }

        /**
         * Add an {@link AtmosphereInterceptor}
         *
         * @param interceptor an {@link AtmosphereInterceptor}
         * @return
         */
        public Builder interceptor(AtmosphereInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }


    }

    private Handler<HttpServerRequest> handleHttp() {

        return new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest req) {
                logger.trace("HTTP received");
                coordinator.route(req);
            }
        };
    }

    private Handler<ServerWebSocket> handleWebSocket() {
        return new Handler<ServerWebSocket>() {
            @Override
            public void handle(ServerWebSocket webSocket) {
                logger.trace("WebSocket received {}", webSocket);
                coordinator.route(webSocket);
            }
        };
    }
}
