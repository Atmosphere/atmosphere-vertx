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
For example, the famous multi-room Chat application in Atmosphere
```java
  @ManagedService(path = "chat/{room: [a-zA-Z][a-zA-Z_0-9]*}")
  public class ChatRoom {
      private final Logger logger = LoggerFactory.getLogger(ChatRoom.class);

      private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<String, String>();
      private String chatroomName;
      private String mappedPath;
      private BroadcasterFactory factory;

      /**
       * Invoked when the connection as been fully established and suspended, e.g ready for receiving messages.
       *
       * @param r
       */
      @Ready(value = Ready.DELIVER_TO.ALL, encoders = {JacksonEncoder.class})
      public ChatProtocol onReady(final AtmosphereResource r) {
          logger.info("Browser {} connected.", r.uuid());
          if (chatroomName == null) {
              mappedPath = r.getBroadcaster().getID();
              // Get rid of the AtmosphereFramework mapped path.
              chatroomName = mappedPath.split("/")[2];
              factory = r.getAtmosphereConfig().getBroadcasterFactory();
          }

          return new ChatProtocol(users.keySet(), factory.lookupAll());
      }

      /**
       * Invoked when the client disconnect or when an unexpected closing of the underlying connection happens.
       *
       * @param event
       */
      @Disconnect
      public void onDisconnect(AtmosphereResourceEvent event) {
          if (event.isCancelled()) {
              // We didn't get notified, so we remove the user.
              users.values().remove(event.getResource().uuid());
              logger.info("Browser {} unexpectedly disconnected", event.getResource().uuid());
          } else if (event.isClosedByClient()) {
              logger.info("Browser {} closed the connection", event.getResource().uuid());
          }
      }

      /**
       * Simple annotated class that demonstrate how {@link org.atmosphere.config.managed.Encoder} and {@link org.atmosphere.config.managed.Decoder
       * can be used.
       *
       * @param message an instance of {@link ChatProtocol }
       * @return
       * @throws IOException
       */
      @Message(encoders = {JacksonEncoder.class}, decoders = {ProtocolDecoder.class})
      public ChatProtocol onMessage(ChatProtocol message) throws IOException {

          if (!users.containsKey(message.getAuthor())) {
              users.put(message.getAuthor(), message.getUuid());
              return new ChatProtocol(message.getAuthor(), " entered room " + chatroomName, users.keySet(), factory.lookupAll());
          }

          if (message.getMessage().contains("disconnecting")) {
              users.remove(message.getAuthor());
              return new ChatProtocol(message.getAuthor(), " disconnected from room " + chatroomName, users.keySet(), factory.lookupAll());
          }

          message.setUsers(users.keySet());
          logger.info("{} just send {}", message.getAuthor(), message.getMessage());
          return new ChatProtocol(message.getAuthor(), message.getMessage(), users.keySet(), factory.lookupAll());
      }

      @Message(decoders = {UserDecoder.class})
      public void onPrivateMessage(UserMessage user) throws IOException {
          String userUUID = users.get(user.getUser());
          if (userUUID != null) {
              AtmosphereResource r = AtmosphereResourceFactory.getDefault().find(userUUID);

              if (r != null) {
                  ChatProtocol m = new ChatProtocol(user.getUser(), " sent you a private message: " + user.getMessage().split(":")[1], users.keySet(), factory.lookupAll());
                  if (!user.getUser().equalsIgnoreCase("all")) {
                      factory.lookup(mappedPath).broadcast(m, r);
                  }
              }
          } else {
              ChatProtocol m = new ChatProtocol(user.getUser(), " sent a message to all chatroom: " + user.getMessage().split(":")[1], users.keySet(), factory.lookupAll());
              MetaBroadcaster.getDefault().broadcastTo("/*", m);
          }
      }

```
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
Same for Jersey. You can run Jersey resource like
```java
@Path("/chat")
public class ResourceChat {

    /**
     * Suspend the response without writing anything back to the client.
     * @return a white space
     */
    @Suspend(contentType = "application/json")
    @GET
    public String suspend() {
        return "";
    }

    /**
     * Broadcast the received message object to all suspended response. Do not write back the message to the calling connection.
     * @param message a {@link Message}
     * @return a {@link Response}
     */
    @Broadcast(writeEntity = false)
    @POST
    @Produces("application/json")
    public Response broadcast(Message message) {
        return new Response(message.author, message.message);
    }

}
```
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