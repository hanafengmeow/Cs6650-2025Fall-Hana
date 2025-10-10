package assignment1.mainphase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class WebSocketManager {
  private final String wsBase;
  private final int connectTimeoutSeconds;
  private final long ackTimeoutMs;

  private final AtomicLong connections;
  private final AtomicLong reconnects;
  private final AtomicLong ok, fail;
  private final MetricsRecorder metrics;

  private final Map<Integer, BasicClient> pool = new ConcurrentHashMap<>();

  WebSocketManager(String wsBase, int connectTimeoutSeconds, long ackTimeoutMs,
                   AtomicLong connections, AtomicLong reconnects,
                   AtomicLong ok, AtomicLong fail, MetricsRecorder metrics) {
    this.wsBase = wsBase;
    this.connectTimeoutSeconds = connectTimeoutSeconds;
    this.ackTimeoutMs = ackTimeoutMs;
    this.connections = connections;
    this.reconnects = reconnects;
    this.ok = ok;
    this.fail = fail;
    this.metrics = metrics;
  }

  BasicClient get(int roomId) throws Exception {
    BasicClient client = pool.get(roomId);
    if (client != null && client.isOpen()) return client;

    synchronized (this) {
      client = pool.get(roomId);
      if (client != null && client.isOpen()) return client;

      boolean isReconnect = (client != null);
      if (client != null) {
        try { client.close(); Thread.sleep(1000); } catch (Exception ignore) {}
      }
      BasicClient newClient = new BasicClient(URI.create(wsBase + roomId), metrics, ok, fail, ackTimeoutMs);
      newClient.connectBlocking(connectTimeoutSeconds, TimeUnit.SECONDS);
      pool.put(roomId, newClient);

      if (isReconnect) reconnects.incrementAndGet();
      else connections.incrementAndGet();
      return newClient;
    }
  }

  void closeAll() {
    for (BasicClient c : pool.values()) {
      try { c.closeBlocking(); } catch (Exception ignore) {}
    }
    pool.clear();
  }

  void expireAll(long nowEpochMs) {
    for (BasicClient c : pool.values()) {
      c.expireOld(nowEpochMs);
    }
  }

  static class BasicClient extends WebSocketClient {
    private static class Pending {
      final long startNano;
      final long sendEpochMs;
      final int roomId;
      final String type;
      Pending(long sNano, long sEpochMs, int r, String t) {
        this.startNano = sNano; this.sendEpochMs = sEpochMs; this.roomId = r; this.type = t;
      }
    }

    private final ArrayBlockingQueue<Pending> pendings = new ArrayBlockingQueue<>(4096);
    private final MetricsRecorder metrics;
    private final AtomicLong ok, fail;
    private final long ackTimeoutMs;

    BasicClient(URI uri, MetricsRecorder metrics, AtomicLong ok, AtomicLong fail, long ackTimeoutMs) {
      super(uri);
      this.metrics = metrics;
      this.ok = ok;
      this.fail = fail;
      this.ackTimeoutMs = ackTimeoutMs;
    }

    @Override public void onOpen(ServerHandshake sh) {}

    @Override public void onMessage(String msg) {
      Pending p = pendings.poll();
      if (p == null) return;

      long end = System.nanoTime();
      long latencyMs = (end - p.startNano) / 1_000_000;

      int code = parseStatusCode(msg);
      if (code == MetricsRecorder.OK) {
        ok.incrementAndGet();
        metrics.record(p.roomId, p.type, latencyMs, MetricsRecorder.OK, p.sendEpochMs);
      } else {
        fail.incrementAndGet();
      }
    }

    @Override public void onClose(int code, String reason, boolean remote) {}
    @Override public void onError(Exception ex) {}

    synchronized void safeSend(String text) { super.send(text); }

    void registerSend(long startNano, long sendEpochMs, int roomId, String messageType) {
      try { pendings.put(new Pending(startNano, sendEpochMs, roomId, messageType)); }
      catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    void expireOld(long nowEpochMs) {
      while (true) {
        Pending head = pendings.peek();
        if (head == null) break;
        long waited = nowEpochMs - head.sendEpochMs;
        if (waited < ackTimeoutMs) break;

        pendings.poll();
        fail.incrementAndGet();
      }
    }

    private static int parseStatusCode(String json) {
      try {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        String status = obj.has("status") ? obj.get("status").getAsString() : "OK";
        return "OK".equalsIgnoreCase(status) ? MetricsRecorder.OK : MetricsRecorder.ERROR;
      } catch (Exception e) {
        return MetricsRecorder.ERROR;
      }
    }
  }
}