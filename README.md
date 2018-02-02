### Vertosphere: A Java WebSocket and HTTP server powered by the [Atmosphere Framework](http://github.com/Atmosphere/atmosphere) and the [Vert.x](http://vertx.io/)  Framework.

The easiest way to get started with Vert.x is to download a sample and start it. [Or look at the Javadoc](http://atmosphere.github.io/atmosphere-vertx/apidocs/). Samples are available [here](https://github.com/Atmosphere/atmosphere-samples/tree/master/vertx-samples)

```bash
   % mvn package; jdebug -jar target/vertx-chat-xxx-fat.jar
```

Samples are the same as then one available in Atmosphere, e.g everything that works with Atmosphere works AS-IT-IS with the Vert.x module.

Download Vert.x extension [) or use Maven

```xml
     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>atmosphere-vertx</artifactId>
         <version>3.0.0</version>
     </dependency>
```
For example, the famous [multi-room Chat application](https://github.com/Atmosphere/atmosphere-samples/blob/master/vertx-samples/chat/src/main/java/org/atmosphere/vertx/samples/chat/VertxChatServer.java) in Atmosphere
Can be run on top of Vert.x by doing:
```java
public class VertxChatServer extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(VertxChatServer.class);

    private HttpServer httpServer;
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
        httpServer = vertx.createHttpServer();
    }

    @Override
    public void start(Future<Void> future) throws Exception {
        VertxAtmosphere.Builder b = new VertxAtmosphere.Builder();

        b.resource(ChatRoom.class).httpServer(httpServer).url("/chat/:room")
                .webroot("src/main/java/org/atmosphere/vertx/samples/webroot/")
                .vertx(vertx)
                .build();
        httpServer.listen(8080);
    }

    @Override
    public void stop(Future<Void> future) throws Exception {
        httpServer.close();
    }
}
```
Same for Jersey. You can run any [Jersey resource](https://github.com/Atmosphere/atmosphere-vertx/blob/master/samples/jersey-chat/src/main/java/org/atmosphere/vertx/samples/chat/ResourceChat.java#L36-L61) like
can be boostrapped by doing
```java
public class VertxJerseyChat extends AbstractVerticle {

    private HttpServer httpServer;
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
        httpServer = vertx.createHttpServer();
    }

    @Override
    public void start(Future<Void> future) throws Exception {
        VertxAtmosphere.Builder b = new VertxAtmosphere.Builder();

        b.resource(ResourceChat.class).httpServer(httpServer).url("/chat/:room")
                .webroot("src/main/webapp/")
                .initParam(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json")
                .vertx(vertx)
                .build();
        httpServer.listen(8080);
    }

    @Override
    public void stop(Future<Void> future) throws Exception {
        httpServer.close();
    }
}
```
[![Analytics](https://ga-beacon.appspot.com/UA-31990725-2/Atmosphere/atmosphere-vertx)]
