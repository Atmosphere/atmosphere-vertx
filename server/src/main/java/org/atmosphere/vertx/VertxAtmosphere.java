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

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.ServerWebSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VertxAtmosphere  {

    private final Builder b;

    private VertxAtmosphere(Builder b) {
        this.b = b;

        RouteMatcher routeMatcher = new RouteMatcher();
		routeMatcher.get(b.url, inject());
		routeMatcher.post(b.url, inject());
		routeMatcher.noMatch(b.httpServer.requestHandler());
		
		b.httpServer.requestHandler(routeMatcher);
		b.httpServer.websocketHandler(injectWS());

        if (b.resource != null) {
            AtmosphereCoordinator.instance().discover(b.resource);
        }
        AtmosphereCoordinator.instance().ready();
	}

   public final static class Builder {

       private String url;
       private HttpServer httpServer;
       private Class<?> resource;

       public Builder url(String url) {
           this.url = url;
           return this;
       }

       public Builder httpServer(HttpServer httpServer) {
           this.httpServer = httpServer;
           return this;
       }

       public VertxAtmosphere build(){
           return new VertxAtmosphere(this);
       }

       public Builder resource(Class<?> resource) {
           this.resource = resource;
           return this;
       }
   }

	private Handler<HttpServerRequest> inject() {
		return new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest req) {
                new VertxAsyncIOWriter(req);
			}
		};
	}


	private Handler<ServerWebSocket> injectWS() {
		return new Handler<ServerWebSocket>() {
			Handler<ServerWebSocket> old = b.httpServer.websocketHandler();
			@Override
			public void handle(ServerWebSocket webSocket) {
				if (webSocket.path.startsWith(b.url)) {
					new VertxWebSocket(AtmosphereCoordinator.instance().framework().getAtmosphereConfig(), webSocket);
				}

				if (old != null) {
					old.handle(webSocket);
				}
			}
		};
	}

	private void write(InputStream in, OutputStream out) throws IOException {
		try {
			byte[] buffer = new byte[4096];
			int bytesRead = -1;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
			out.flush();
		} finally {
			try {
				in.close();
			} catch (IOException ex) {}
			try {
				out.close();
			} catch (IOException ex) {}
		}
	}

}
