package assignment1.mainphase;

public class MainConfig {
  //fixed parameters
  public final String wsBase = System.getProperty("WS_BASE", "ws://localhost:8080/chat/");
  public final long total = Long.parseLong(System.getProperty("TOTAL", "500000"));
  public final int rooms = Integer.parseInt(System.getProperty("ROOMS", "20"));

  //dynamic parameters
  // Default: max(8, CPU cores * 4). Tune range: cores*2 ~ cores*4.
  // Larger -> higher parallelism, but more in-flight and queueing on connections.
  public final int senders = Integer.parseInt(System.getProperty("SENDERS",
      Integer.toString(Math.max(8, Runtime.getRuntime().availableProcessors() * 4))));

  //Sender side queue capacity
  //Tune range: 10000 ~ 100000
  //Too small -> sender will block
  //Too large -> memory usage goes up
  public final int queueCap = Integer.parseInt(System.getProperty("QUEUE_CAP", "50000"));

  //Timeout for acknowledgement from server，if no echo by this deadline, count as failure.
  //First ack was 2000, caused many failures but no retries
  public final long ackTimeoutMs = Long.getLong("ACK_TIMEOUT_MS", 60000L);


  //Other
// 每房间的有效 in‑flight（仅用于预测，不影响真实发送）
  public final int predictKPerRoom = Integer.getInteger("PREDICT_K_PER_ROOM", 600);
  public final String csvPrefix = System.getProperty("CSV_PREFIX", "run");
  public final long bucketSec = Long.parseLong(System.getProperty("BUCKET_SEC", "10"));
  public final int connectTimeoutSeconds = Integer.getInteger("CONNECT_TIMEOUT_SECONDS", 10);
  public final double predictRttMs = Double.parseDouble(System.getProperty("PREDICT_RTT_MS", "50"));
  public final double connectionOverheadMs = Double.parseDouble(System.getProperty("CONNECTION_OVERHEAD_MS", "5"));
  public final String csvDir   = System.getProperty("CSV_DIR",   "client-part2/results");
  public final String chartDir = System.getProperty("CHART_DIR", "client-part2/charts");
}


// My good parameters:
// -DSENDERS=32 -DQUEUE_CAP=50000 -DACK_TIMEOUT_MS=60000