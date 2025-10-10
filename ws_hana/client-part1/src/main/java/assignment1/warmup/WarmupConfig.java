package assignment1.warmup;

public class WarmupConfig {
  public final int threads = Integer.getInteger("THREADS", 32);
  public final int messagesPerThread = Integer.getInteger("MESSAGE_PER_THREAD", 1000);

  public final String wsBase = System.getProperty("WS_BASE", "ws://localhost:8080/chat/");
  public final String roomId = System.getProperty("ROOM_ID", "1");

  public final long ackTimeoutMs = Long.getLong("ACK_TIMEOUT_MS", 2000L);
  public final int connectTimeoutSeconds = Integer.getInteger("CONNECT_TIMEOUT_SECONDS", 10);
  public final double fallbackLatencyMs = Double.parseDouble(System.getProperty("FALLBACK_LATENCY_MS", "50"));
  public final double connectionOverheadMs = Double.parseDouble(System.getProperty("CONNECTION_OVERHEAD_MS", "5"));
}