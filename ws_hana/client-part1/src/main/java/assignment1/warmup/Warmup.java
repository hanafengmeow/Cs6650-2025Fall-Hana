package assignment1.warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Warmup {

  public static void main(String[] args) throws Exception {
    WarmupConfig cfg = new WarmupConfig();
    final String target = cfg.wsBase + cfg.roomId;

    AtomicLong successCount = new AtomicLong();
    AtomicLong failCount = new AtomicLong();
    AtomicLong connectionCount = new AtomicLong();
    AtomicLong reconnectionCount = new AtomicLong();

    WarmupPerformance analyzer = new WarmupPerformance(cfg, target);
    analyzer.measureLatency();
    analyzer.printPrediction();

    System.out.println("\n=== Starting Warmup Phase ===");
    System.out.println("Configuration:");
    System.out.println("  Threads: " + cfg.threads);
    System.out.println("  Messages per thread: " + cfg.messagesPerThread);
    long totalMessages = (long) cfg.threads * cfg.messagesPerThread;
    System.out.println("  Total messages: " + totalMessages);
    System.out.println("  Target server: " + target);
    System.out.println();

    List<ThreadManager> threads = new ArrayList<>();
    for (int i = 0; i < cfg.threads; i++) {
      ThreadManager thread = new ThreadManager(
          target, cfg.messagesPerThread, cfg.ackTimeoutMs, cfg.connectTimeoutSeconds,
          successCount, failCount, connectionCount, reconnectionCount);
      thread.setName("warmup-" + i);
      threads.add(thread);
    }

    long startTime = System.nanoTime();
    threads.forEach(Thread::start);

    for (Thread thread : threads) {
      thread.join();
    }
    long endTime = System.nanoTime();

    analyzer.printRunResultsAndComparison(
        startTime, endTime, totalMessages,
        successCount, failCount, connectionCount, reconnectionCount);
  }
}