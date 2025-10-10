package assignment1.warmup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Little's Law calculations and performance reporting.
 */
public class WarmupPerformance {
  private static final Gson gson = new Gson();

  private final int threads;
  private final String serverUrl;
  private final int connectTimeoutSeconds;
  private final long ackWaitMs;

  private double measuredLatencyMs;
  private final double fallbackLatencyMs;

  public WarmupPerformance(WarmupConfig cfg, String serverUrl) {
    this.threads = cfg.threads;
    this.serverUrl = serverUrl;
    this.connectTimeoutSeconds = cfg.connectTimeoutSeconds;
    this.ackWaitMs = cfg.ackTimeoutMs;
    this.fallbackLatencyMs = cfg.fallbackLatencyMs;
  }

  public void measureLatency() {
    System.out.println("Measuring single message round-trip time...");
    try {
      WarmupClient client = new WarmupClient(URI.create(serverUrl));
      boolean connected = client.connectBlocking(connectTimeoutSeconds, TimeUnit.SECONDS);

      if (!connected) {
        measuredLatencyMs = fallbackLatencyMs;
        System.out.println("  Could not connect, using fallback: " + (long) fallbackLatencyMs + "ms");
        return;
      }

      long totalNanos = 0;
      int samples = 50;
      int ok = 0;

      for (int i = 0; i < samples; i++) {
        String testMsg = generateTestMessage();
        client.resetAck();
        long start = System.nanoTime();
        client.send(testMsg);
        boolean got = client.awaitAck(ackWaitMs);
        long end = System.nanoTime();

        if (!got) continue;
        totalNanos += (end - start);
        ok++;
        Thread.sleep(5);
      }

      client.closeBlocking();
      if (ok == 0) {
        measuredLatencyMs = fallbackLatencyMs;
        System.out.println("  No successful acks, using fallback: " + (long) fallbackLatencyMs + "ms");
      } else {
        measuredLatencyMs = (totalNanos / (double) ok) / 1_000_000.0;
        System.out.println("  Measured latency: " + String.format("%.2f", measuredLatencyMs) + "ms");
      }

    } catch (Exception e) {
      measuredLatencyMs = fallbackLatencyMs;
      System.out.println("  Error measuring, using fallback: " + (long) fallbackLatencyMs + "ms");
    }
  }

  public void printPrediction() {
    System.out.println("=== Little's Law Analysis ===");
    System.out.println("\n1. Single message round-trip time:");
    System.out.println("  Measured latency: " + String.format("%.2f", measuredLatencyMs) + "ms");

    System.out.println("\n2. Average response time used for prediction:");
    System.out.println("  Using measured latency only (no per-message connection overhead)");
    double avgResponseTimeSeconds = measuredLatencyMs / 1000.0;
    System.out.println("  W (avg response time): " +
        String.format("%.4f", avgResponseTimeSeconds) + "s");

    System.out.println("\n3. Predicting maximum throughput (Little's Law):");
    System.out.println("  λ = L / W");
    System.out.println("  L (concurrent threads) = " + threads);
    System.out.println("  W (avg response time) = " +
        String.format("%.4f", avgResponseTimeSeconds) + "s");
    double predictedThroughput = threads / avgResponseTimeSeconds;
    System.out.println("  λ (predicted throughput) = " +
        String.format("%.1f", predictedThroughput) + " messages/second");
  }

  public double calculatePredictedThroughput() {
    double avgResponseTimeSeconds = measuredLatencyMs / 1000.0;
    return threads / avgResponseTimeSeconds;
  }

  public void printRunResultsAndComparison(
      long startNano, long endNano, long totalMessages,
      AtomicLong successCount, AtomicLong failCount,
      AtomicLong connectionCount, AtomicLong reconnectionCount) {

    double durationSeconds = (endNano - startNano) / 1_000_000_000.0;
    double throughput = successCount.get() / durationSeconds;
    double successRate = (successCount.get() * 100.0) / totalMessages;

    System.out.println("\n=== Warmup Phase Complete ===");
    System.out.println("\nResults:");
    System.out.println("  Total messages attempted: " + totalMessages);
    System.out.println("  Successful messages sent: " + successCount.get());
    System.out.println("  Failed messages: " + failCount.get());
    System.out.println("  Success rate: " + String.format("%.2f", successRate) + "%");
    System.out.println("  Total runtime (wall time): " +
        String.format("%.3f", durationSeconds) + " seconds");
    System.out.println("  Overall throughput: " +
        String.format("%.1f", throughput) + " messages/second");


    System.out.println("\nConnection Statistics:");
    System.out.println("  Total connections: " + connectionCount.get());
    System.out.println("  Reconnections: " + reconnectionCount.get());

    printComparison(throughput);
  }

  public void printComparison(double actualThroughput) {
    System.out.println("\n=== Performance Comparison ===");
    double predictedThroughput = calculatePredictedThroughput();
    double ratio = (predictedThroughput == 0) ? 0.0 : (actualThroughput / predictedThroughput);
    double errorPct = (predictedThroughput == 0) ? 0.0 :
        ((actualThroughput - predictedThroughput) / predictedThroughput) * 100.0;

    System.out.println("  Predicted throughput: " +
        String.format("%.1f", predictedThroughput) + " messages/second");
    System.out.println("  Actual throughput: " +
        String.format("%.1f", actualThroughput) + " messages/second");
    System.out.println("  Actual/Predicted ratio: " + String.format("%.2f", ratio));
    System.out.println("  Error: " + String.format("%.1f", errorPct) + "%");
  }

  private String generateTestMessage() {
    JsonObject obj = new JsonObject();
    obj.addProperty("userId", "1");
    obj.addProperty("username", "testuser");
    obj.addProperty("message", "Latency test message");
    obj.addProperty("timestamp", Instant.now().toString());
    obj.addProperty("messageType", "TEXT");
    return gson.toJson(obj);
  }
}