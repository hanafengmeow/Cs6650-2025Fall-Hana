package assignment1.warmup;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class WarmupClient extends WebSocketClient {
  private final Semaphore acks = new Semaphore(0);

  WarmupClient(URI serverUri) {
    super(serverUri);
  }

  void resetAck() { acks.drainPermits(); }

  boolean awaitAck(long timeoutMs) {
    try {
      return acks.tryAcquire(1, timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override public void onOpen(ServerHandshake handsh) {}
  @Override public void onMessage(String message) { acks.release(); }
  @Override public void onClose(int code, String reason, boolean remote) {}
  @Override public void onError(Exception ex) {}
}