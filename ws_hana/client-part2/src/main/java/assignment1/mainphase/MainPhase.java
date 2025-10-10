package assignment1.mainphase;

import assignment1.mainphase.MessageGenerator.Outbound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MainPhase {
  private static final Logger log = LoggerFactory.getLogger(MainPhase.class);

  public static void main(String[] args) throws Exception {
    MainConfig cfg = new MainConfig();

    // unique run id and file paths
    String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    Path csvDir = Paths.get(cfg.csvDir);
    Files.createDirectories(csvDir);
    Path csvFile = csvDir.resolve(cfg.csvPrefix + "_" + runId + ".csv");

    log.info("Assignment 1 - Main Phase Client");
    log.info("Configuration: WS_BASE={}, TOTAL={}, ROOMS={}, SENDERS={}, QUEUE_CAP={}, CSV_DIR={}, CSV_PREFIX={}, BUCKET_SEC={}",
        cfg.wsBase, cfg.total, cfg.rooms, cfg.senders, cfg.queueCap, cfg.csvDir, cfg.csvPrefix, cfg.bucketSec);

    // producer -> sender buffer (backpressure)
    BlockingQueue<Outbound> q = new ArrayBlockingQueue<>(cfg.queueCap);

    // counters
    AtomicLong ok = new AtomicLong();
    AtomicLong fail = new AtomicLong();
    AtomicLong retries = new AtomicLong();
    AtomicLong reconnects = new AtomicLong();
    AtomicLong connections = new AtomicLong();
    AtomicLong sendFailAfter5 = new AtomicLong();

    long startWallMs = System.currentTimeMillis();
    try (MetricsRecorder recorder = new MetricsRecorder(csvFile.toString(), cfg.bucketSec, startWallMs)) {

      WebSocketManager pool = new WebSocketManager(
          cfg.wsBase, cfg.connectTimeoutSeconds, cfg.ackTimeoutMs,
          connections, reconnects, ok, fail, recorder);

      Thread producer = new Thread(new MessageGenerator(q, cfg.total, cfg.rooms), "producer");

      List<Thread> workers = new ArrayList<>();
      for (int i = 0; i < cfg.senders; i++) {
        workers.add(new Thread(new SenderWorker(q, pool, fail, retries, sendFailAfter5), "sender-" + i));
      }

      long t0 = System.nanoTime();
      producer.start();
      workers.forEach(Thread::start);

      try {
        producer.join();

        long lastProgressTime = System.currentTimeMillis();
        long startWaitTime = System.currentTimeMillis();
        long timeoutMs = TimeUnit.MINUTES.toMillis(10);

        while (ok.get() + fail.get() < cfg.total) {
          long now = System.currentTimeMillis();

          // convert timed-out pendings to failures (no CSV)
          pool.expireAll(now);

          long processed = ok.get() + fail.get();
          if (now - startWaitTime > timeoutMs) {
            System.err.println("ERROR: Timeout - only processed " + processed + "/" + cfg.total + " messages");
            break;
          }
          if (now - lastProgressTime > 5000) {
            double progress = 100.0 * processed / cfg.total;
            log.info("Progress: {}/{} messages ({}%), queue size: {}, ok: {}, fail: {}, retries: {}",
                processed, cfg.total, String.format("%.1f", progress), q.size(), ok.get(), fail.get(), retries.get());
            lastProgressTime = now;
          }
          Thread.sleep(200);
        }
      } finally {
        for (Thread t : workers) t.interrupt();
        for (Thread t : workers) t.join(2000);
        if (producer.isAlive()) { producer.interrupt(); producer.join(1000); }
        pool.closeAll();
      }

      long t1 = System.nanoTime();
      double secs = (t1 - t0) / 1_000_000_000.0;

      recorder.printReports(
          cfg, cfg.total, secs, cfg.predictRttMs,
          ok.get(), fail.get(), retries.get(), connections.get(), reconnects.get(), sendFailAfter5.get());

      // append one-line run summary
      RunSummaryWriter.append(
          cfg, runId, csvFile.toString(),
          cfg.total, secs, cfg.predictRttMs,
          ok.get(), fail.get(), sendFailAfter5.get(), retries.get(), connections.get(), reconnects.get(),
          recorder.summarize());

      // write throughput chart PNG to charts directory (does not affect measurement)
      ThroughputPlot.writePng(cfg, runId, recorder.summarize(), cfg.total, secs);
    }
  }
}