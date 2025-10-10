package assignment1.warmup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker thread: one WebSocket connection, send N messages (send->echo).
 */
class ThreadManager extends Thread {
  private static final Gson gson = new Gson();
  private static final int MAX_RETRIES = 5;

  private final String serverUrl;
  private final int messagesToSend;
  private final long ackTimeoutMs;
  private final int connectTimeoutSeconds;

  private final AtomicLong successCount;
  private final AtomicLong failCount;
  private final AtomicLong connectionCount;
  private final AtomicLong reconnectionCount;

  private int sentCount = 0;

  ThreadManager(String serverUrl, int messagesToSend, long ackTimeoutMs, int connectTimeoutSeconds,
      AtomicLong successCount, AtomicLong failCount,
      AtomicLong connectionCount, AtomicLong reconnectionCount) {
    this.serverUrl = serverUrl;
    this.messagesToSend = messagesToSend;
    this.ackTimeoutMs = ackTimeoutMs;
    this.connectTimeoutSeconds = connectTimeoutSeconds;
    this.successCount = successCount;
    this.failCount = failCount;
    this.connectionCount = connectionCount;
    this.reconnectionCount = reconnectionCount;
  }

  @Override public void run() {
    WarmupClient client = null;
    try {
      client = new WarmupClient(URI.create(serverUrl));
      connectionCount.incrementAndGet();

      boolean connected = client.connectBlocking(connectTimeoutSeconds, TimeUnit.SECONDS);
      if (!connected) {
        System.err.println(getName() + ": Failed to connect to " + serverUrl);
        failCount.addAndGet(messagesToSend);
        return;
      }

      for (int i = 0; i < messagesToSend; i++) {
        String json = generateMessage();
        boolean sent = sendWithRetryAwaitAck(client, json);

        if (sent) {
          sentCount++;
          successCount.incrementAndGet();
        } else {
          failCount.incrementAndGet();
        }
      }

    } catch (Exception e) {
      System.err.println(getName() + ": Thread error: " + e.getMessage());
      failCount.addAndGet(messagesToSend - sentCount);
    } finally {
      if (client != null) {
        try { client.closeBlocking(); } catch (Exception ignored) {}
      }
    }
  }

  private boolean sendWithRetryAwaitAck(WarmupClient client, String json) {
    long backoff = 10;
  
    for (int retry = 0; retry < MAX_RETRIES; retry++) {
      try {
        if (!client.isOpen()) {
          reconnectionCount.incrementAndGet();
          boolean ok = client.reconnectBlocking();
          if (!ok) {
            throw new IOException("Reconnect failed");
          }
        }
  
        client.resetAck();
        client.send(json);
  
        if (!client.awaitAck(ackTimeoutMs)) {
          throw new IOException("Ack timeout");
        }
        return true;
  
      } catch (Exception e) {
        if (retry == MAX_RETRIES - 1) {
          System.err.println(getName() + ": Failed after " + MAX_RETRIES + " retries");
          return false;
        }
        try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
        backoff = Math.min(200, backoff * 2);
      }
    }
    return false;
  }

  private String generateMessage() {
    int uid = 1 + ThreadLocalRandom.current().nextInt(100_000);
    String userId = Integer.toString(uid);
    String username = "user" + userId;
    String message = "Warmup message";
    String ts = Instant.now().toString();
    String messageType = "TEXT";

    JsonObject obj = new JsonObject();
    obj.addProperty("userId", userId);
    obj.addProperty("username", username);
    obj.addProperty("message", message);
    obj.addProperty("timestamp", ts);
    obj.addProperty("messageType", messageType);

    return gson.toJson(obj);
  }
}