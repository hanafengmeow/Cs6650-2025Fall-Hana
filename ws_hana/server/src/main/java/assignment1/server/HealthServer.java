package assignment1.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class HealthServer {
  private final int port;
  private static final Gson gson = new Gson();

  public HealthServer(int port) {
    this.port = port;
  }

  public void start() throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
    httpServer.createContext("/health", new HealthHandler());
    httpServer.setExecutor(null);
    httpServer.start();
    System.out.println("HTTP /health endpoint started on port " + port);
  }

  static class HealthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
        return;
      }

      Map<String, Object> response = new HashMap<>();
      response.put("status", "UP");
      response.put("timestamp", Instant.now().toString());
      response.put("uptimeSeconds", (System.currentTimeMillis() - Main.START_TIME) / 1000);

      sendResponse(exchange, 200, gson.toJson(response));
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response)
        throws IOException {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(statusCode, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    }
  }
}
