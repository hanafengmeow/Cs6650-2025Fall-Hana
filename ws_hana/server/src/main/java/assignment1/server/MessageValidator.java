package assignment1.server;

import java.time.Instant;

public class MessageValidator {

  public String validate(ChatMessage msg) {
    if (msg == null) return "Null message body";

    if (msg.userId == null || msg.userId.isBlank()) return "userId missing";
    try {
      int uid = Integer.parseInt(msg.userId);
      if (uid < 1 || uid > 100000) return "userId must be 1-100000";
    } catch (NumberFormatException e) {
      return "userId must be numeric";
    }

    if (msg.username == null || msg.username.isBlank()) return "username missing";
    if (!msg.username.matches("^[A-Za-z0-9]{3,20}$")) {
      return "username must be 3-20 alphanumeric characters";
    }

    if (msg.message == null || msg.message.isBlank()) return "message missing";
    if (msg.message.isEmpty() || msg.message.length() > 500) {
      return "message must be 1-500 characters";
    }

    if (msg.timestamp == null || msg.timestamp.isBlank()) return "timestamp missing";
    try {
      Instant.parse(msg.timestamp);
    } catch (Exception e) {
      return "timestamp must be valid ISO-8601 format";
    }

    if (msg.messageType == null || msg.messageType.isBlank()) return "messageType missing";
    if (!msg.messageType.matches("TEXT|JOIN|LEAVE")) {
      return "messageType must be TEXT, JOIN, or LEAVE";
    }

    return null; // Valid
  }
}
