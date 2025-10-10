package assignment1.server;

import com.google.gson.Gson;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketChatServer extends WebSocketServer {
  private static final Gson gson = new Gson();
  private final MessageValidator validator = new MessageValidator();
  private final ConcurrentHashMap<WebSocket, Integer> connectionRooms = new ConcurrentHashMap<>();
  private final int maxConnections;

  public WebSocketChatServer(int port, int maxConnections) {
    super(new InetSocketAddress(port));
    this.maxConnections = maxConnections;
    setConnectionLostTimeout(30); // ping/close detection
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    if (getConnections().size() > maxConnections) {
      System.err.println("Server at capacity (" + maxConnections + "), rejecting");
      conn.close(1008, "Server at capacity");
      return;
    }

    String path = handshake.getResourceDescriptor(); // e.g., /chat/1
    String[] parts = path.split("/");
    if (parts.length < 3 || !"chat".equals(parts[1])) {
      conn.close(1003, "Invalid path - expected /chat/{roomId}");
      return;
    }

    try {
      int roomId = Integer.parseInt(parts[2]);
      connectionRooms.put(conn, roomId);
      // no per-connection println to keep server fast
    } catch (NumberFormatException e) {
      conn.close(1003, "Invalid roomId - must be numeric");
    }
  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    Integer roomId = connectionRooms.get(conn);
    ChatMessage response;

    try {
      ChatMessage chatMsg = gson.fromJson(message, ChatMessage.class);
      String error = validator.validate(chatMsg);

      if (error == null) {
        // valid → echo with server timestamp/status
        response = ResponseMsg.success(chatMsg);
      } else {
        // validation error → send error JSON (no per-message println)
        response = ResponseMsg.error(error);
      }
    } catch (Exception e) {
      // malformed JSON
      response = ResponseMsg.error("Malformed JSON: " + e.getMessage());
    }

    try {
      conn.send(gson.toJson(response));
    } catch (Exception e) {
      System.err.println("Send failed to " + conn.getRemoteSocketAddress() +
          " room=" + roomId + " error=" + e.getMessage());
    }
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    connectionRooms.remove(conn);
  }

  @Override
  public void onError(WebSocket conn, Exception ex) {
    System.err.println("WebSocket error: " + ex.getMessage());
    if (conn != null) {
      connectionRooms.remove(conn);
    }
  }

  @Override
  public void onStart() {
    System.out.println("WebSocket server started on port " + getPort());
  }
}