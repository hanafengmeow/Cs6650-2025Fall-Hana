package assignment1.mainphase;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MetricsRecorder implements AutoCloseable {
  public static final int OK = 200;
  public static final int ERROR = 400;

  private final String csvPath;
  private final long bucketSeconds;
  private final long startEpochMs;

  private final LinkedBlockingQueue<Record> queue = new LinkedBlockingQueue<>();
  private final Thread writer;
  private volatile boolean running = true;

  private final List<Long> latencies = new ArrayList<>();
  private final Map<Integer, Long> perRoom = new HashMap<>();
  private final Map<String, Long> perType = new HashMap<>();
  private final Map<Long, Long> perBucket = new HashMap<>();

  public MetricsRecorder(String csvPath, long bucketSeconds, long startEpochMs) {
    this.csvPath = csvPath;
    this.bucketSeconds = bucketSeconds;
    this.startEpochMs = startEpochMs;

    try {
      Path p = Paths.get(csvPath).getParent();
      if (p != null) Files.createDirectories(p);
    } catch (Exception ignored) {}

    this.writer = new Thread(this::drain, "metrics-writer");
    this.writer.start();
  }

  // 仅在成功回显时调用；statusCode 恒为 OK（按作业要求）
  public void record(int roomId, String messageType, long latencyMs, int statusCode, long sendEpochMs) {
    queue.offer(new Record(sendEpochMs, roomId, messageType, latencyMs, statusCode));
  }

  private void drain() {
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvPath))) {
      bw.write("timestamp,messageType,latencyMs,statusCode,roomId\n");

      while (running || !queue.isEmpty()) {
        Record r = queue.poll(200, TimeUnit.MILLISECONDS);
        if (r == null) continue;

        bw.write(r.timestamp + "," + r.messageType + "," + r.latencyMs + "," + r.statusCode + "," + r.roomId + "\n");

        latencies.add(r.latencyMs);
        perRoom.merge(r.roomId, 1L, Long::sum);
        perType.merge(r.messageType, 1L, Long::sum);

        long bucketIdx = ((r.timestamp - startEpochMs) / 1000) / bucketSeconds;
        perBucket.merge(bucketIdx, 1L, Long::sum);
      }
      bw.flush();
    } catch (Exception ignored) {}
  }

  public Summary summarize() {
    long[] arr = latencies.stream().mapToLong(Long::longValue).toArray();
    Arrays.sort(arr);
    long n = arr.length;
    long min = (n == 0) ? 0 : arr[0];
    long max = (n == 0) ? 0 : arr[(int) n - 1];
    double mean = 0.0;
    for (long v : arr) mean += v;
    mean = (n == 0) ? 0.0 : mean / n;

    long p50 = percentile(arr, 50);
    long p95 = percentile(arr, 95);
    long p99 = percentile(arr, 99);

    return new Summary(mean, p50, p95, p99, min, max,
        new TreeMap<>(perRoom), new TreeMap<>(perType), new TreeMap<>(perBucket));
  }

  private static long percentile(long[] s, int p) {
    if (s.length == 0) return 0;
    int idx = (int) Math.ceil(p / 100.0 * s.length) - 1;
    if (idx < 0) idx = 0;
    if (idx >= s.length) idx = s.length - 1;
    return s[idx];
  }

  @Override
  public void close() {
    running = false;
    try { writer.join(2000); } catch (InterruptedException ignored) {}
  }

  public void printReports(MainConfig cfg, long totalMessages, double wallSecs, double assumedRttMs,
                           long ok, long fail, long retries, long connections, long reconnects, long sendFailAfter5) {

    double actualThr = (wallSecs > 0) ? (totalMessages / wallSecs) : 0.0;

    System.out.println("\n=== Basic Performance Metrics ===");
    System.out.printf("Successful messages: %d%n", ok);
    System.out.printf("Failed messages: %d%n", fail);
    System.out.printf("Wall time (s): %.3f%n", wallSecs);
    System.out.printf("Throughput (msg/s): %.1f%n", actualThr);
    System.out.printf("Connections: %d%n", connections);
    System.out.printf("Reconnections: %d%n", reconnects);
    System.out.printf("Send-fail-after-5-retries: %d%n", sendFailAfter5);

    double W = (cfg.predictRttMs + cfg.connectionOverheadMs) / 1000.0; // seconds
    double L = (double) cfg.rooms * cfg.predictKPerRoom;               // rooms × K
    double predicted = L / Math.max(1e-6, W);

    System.out.println("\n=== Little's Law Analysis ===");
    System.out.printf("RTT (assumed, ms): %.2f%n", cfg.predictRttMs);
    System.out.printf("Connection overhead (ms): %.1f%n", cfg.connectionOverheadMs);
    System.out.printf("W (avg response time, s): %.4f%n", W);
    System.out.printf("Assumption: L = rooms(%d) × K(%d) = %.0f%n", cfg.rooms, cfg.predictKPerRoom, L);
    System.out.printf("Predicted throughput (msg/s): %.1f%n", predicted);
    System.out.printf("Actual throughput (msg/s): %.1f%n", actualThr);
    System.out.printf("Accuracy (actual/predicted): %.1f%%%n", (actualThr / predicted) * 100.0);

    // —— Advanced Statistical Analysis —— 
    Summary s = summarize();
    System.out.println("\n=== Advanced Statistical Analysis ===");
    System.out.printf("Mean latency (ms): %.2f%n", s.meanMs());
    System.out.printf("Median latency (ms): %d%n", s.p50Ms());
    System.out.printf("95th percentile (ms): %d%n", s.p95Ms());
    System.out.printf("99th percentile (ms): %d%n", s.p99Ms());
    System.out.printf("Min/Max latency (ms): %d/%d%n", s.minMs(), s.maxMs());

    System.out.println("\nThroughput per room:");
    for (var e : s.throughputPerRoom().entrySet()) {
      System.out.printf("  room %d: %d%n", e.getKey(), e.getValue());
    }

    System.out.println("\nMessage type distribution:");
    for (var e : s.countPerType().entrySet()) {
      System.out.printf("  %s: %d%n", e.getKey(), e.getValue());
    }

    System.out.println("\nThroughput over time (bucket " + cfg.bucketSec + "s):");
    for (var e : s.bucketCounts().entrySet()) {
      long bucketStart = e.getKey() * cfg.bucketSec;
      System.out.printf("  [%ds - %ds): %d%n", bucketStart, bucketStart + cfg.bucketSec, e.getValue());
    }
  }

  public record Summary(
      double meanMs, long p50Ms, long p95Ms, long p99Ms, long minMs, long maxMs,
      Map<Integer, Long> throughputPerRoom, Map<String, Long> countPerType, Map<Long, Long> bucketCounts) {}

  private record Record(long timestamp, int roomId, String messageType, long latencyMs, int statusCode) {}
}