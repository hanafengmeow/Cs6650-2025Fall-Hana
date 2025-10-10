package assignment1.server;

import java.time.Instant;

public class ResponseMsg {

  public static ChatMessage success(ChatMessage original) {
    ChatMessage response = new ChatMessage();
    response.status = "OK";
    response.userId = original.userId;
    response.username = original.username;
    response.message = original.message;
    response.timestamp = original.timestamp;
    response.messageType = original.messageType;
    response.serverTimestamp = Instant.now().toString();
    return response;
  }

  public static ChatMessage error(String errorMessage) {
    ChatMessage response = new ChatMessage();
    response.status = "ERROR";
    response.error = errorMessage;
    response.serverTimestamp = Instant.now().toString();
    return response;
  }
}
