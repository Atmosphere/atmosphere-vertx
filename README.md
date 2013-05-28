### Vertosphere: A Java WebSocket and HTTP server powered by the [Atmosphere Framework](http://github.com/Atmosphere/atmosphere) and the [Vert.x](http://vertx.io/)  Framework.

The easiest way to get started with Vert.x is to download a sample and start it. [Or look at the Javadoc](http://atmosphere.github.com/atmopshere-vertx/apidocs/). You can download the [Chat]() or [Jersey]() distribution.

```bash
   % unzip vertx-<name>-distribution.jar
   % vertx
```

Samples are the same as then one available in Atmosphere, e.g everything that works with Atmosphere works AS-IT-IS with the Vert.x module.

Download Vert.x extension [) or use Maven

```xml
     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>atmosphere-vertx</artifactId>
         <version>1.0.0.beta1</version>
     </dependency>
```
For example, the famous [multi-room Chat application](https://github.com/Atmosphere/atmosphere-vertx/blob/master/samples/chat/src/main/java/org/atmosphere/vertx/samples/chat/ChatRoom.java#L37-L124) in Atmosphere
Can be run on top of Vert.x by doing:
```java
public class VertxChatServer extends Verticle {

    private static final Logger logger = LoggerFactory.getLogger(VertxChatServer.class);

    @Override
    public void start() throws Exception {
        VertxAtmosphere.Builder b = new VertxAtmosphere.Builder();
        HttpServer httpServer = vertx.createHttpServer();

        httpServer.requestHandler(new Handler<HttpServerRequest>() {
            public void handle(HttpServerRequest req) {
                String path = req.path;
                if (path.equals("/")) {
                    path = "/index.html";
                }

                logger.info("Servicing request {}", path);
                req.response.sendFile("src/main/resources" + path);
            }
        });

        b.resource(ChatRoom.class).httpServer(httpServer).url("/chat/:room").build();

        httpServer.listen(8080);
    }
}
```
Same for Jersey. You can run any [Jersey resource](https://github.com/Atmosphere/atmosphere-vertx/blob/master/samples/jersey-chat/src/main/java/org/atmosphere/vertx/samples/chat/ResourceChat.java#L36-L61) like
can be boostrapped by doing
```java
public class VertxJerseyChat extends Verticle {

    private static final Logger logger = LoggerFactory.getLogger(VertxJerseyChat.class);

    @Override
    public void start() throws Exception {
        VertxAtmosphere.Builder b = new VertxAtmosphere.Builder();
        HttpServer httpServer = vertx.createHttpServer();

        httpServer.requestHandler(new Handler<HttpServerRequest>() {
            public void handle(HttpServerRequest req) {
                String path = req.path;
                if (path.equals("/")) {
                    path = "/index.html";
                }

                logger.info("Servicing request {}", path);
                req.response.sendFile("src/main/resources" + path);
            }
        });

        b.resource(ResourceChat.class).initParam(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json").httpServer(httpServer).url("/chat").build();

        httpServer.listen(8080);
    }
}
```