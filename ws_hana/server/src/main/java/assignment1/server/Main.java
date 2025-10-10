package assignment1.server;

public class Main {
  static final long START_TIME = System.currentTimeMillis();

  public static void main(String[] args) {
    try {
      int wsPort = 8080;
      int httpPort = 8081;
      int maxConnections = 500;

      WebSocketChatServer wsServer = new WebSocketChatServer(wsPort, maxConnections);
      wsServer.start();

      HealthServer httpServer = new HealthServer(httpPort);
      httpServer.start();

      System.out.println("Server started successfully");
      System.out.println("WebSocket: ws://localhost:" + wsPort + "/chat/{roomId}");
      System.out.println("Health:    http://localhost:" + httpPort + "/health");
      System.out.println("Max connections: " + maxConnections);

    } catch (Exception e) {
      System.err.println("Server failed to start: " + e.getMessage());
      System.exit(1);
    }
  }
}
