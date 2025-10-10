package assignment1.mainphase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

final class RunSummaryWriter {
  private RunSummaryWriter() {}

  private static String esc(String v) {
    if (v == null) return "";
    if (v.contains(",") || v.contains("\"")) return "\"" + v.replace("\"", "\"\"") + "\"";
    return v;
  }

  private static String moduleRelative(String fileName) {
    try {
      Path jar = Paths.get(RunSummaryWriter.class
          .getProtectionDomain().getCodeSource().getLocation().toURI());
      Path moduleDir;
      if (jar.toString().endsWith(".jar")) {
        moduleDir = jar.getParent().getParent();
      } else {
        moduleDir = jar.getParent().getParent();
      }
      return moduleDir.resolve(fileName).toString();
    } catch (Exception e) {
      return "client-part2" + File.separator + fileName;
    }
  }

  static void append(MainConfig cfg, String runId, String csvFilePath,
                     long totalMessages, double wallSecs, double assumedRttMs,
                     long ok, long fail, long sendFailAfter5, long retries,
                     long connections, long reconnects,
                     MetricsRecorder.Summary s) {
    String summaryPathProp = System.getProperty("SUMMARY_PATH", "").trim();
    String summaryPath = summaryPathProp.isEmpty()
        ? moduleRelative("summary.csv")
        : summaryPathProp;

    double throughput = (wallSecs > 0) ? totalMessages / wallSecs : 0.0;
    double W = (assumedRttMs + cfg.connectionOverheadMs) / 1000.0;
    double predicted = (W > 0) ? cfg.senders / W : 0.0;
    double acc = (predicted > 0) ? (throughput / predicted) * 100.0 : 0.0;

    try {
      Path p = Paths.get(summaryPath);
      Path parent = p.getParent();
      if (parent != null) Files.createDirectories(parent);

      boolean newFile = !Files.exists(p);
      try (BufferedWriter bw = new BufferedWriter(new FileWriter(summaryPath, true))) {
        if (newFile) {
          bw.write(String.join(",",
              "ts","runId",
              "WS_BASE","TOTAL","ROOMS","SENDERS","QUEUE_CAP",
              "ACK_TIMEOUT_MS","CONNECT_TIMEOUT_SECONDS","PREDICT_RTT_MS","CONNECTION_OVERHEAD_MS",
              "ok","fail","sendFailAfter5","retries","connections","reconnections",
              "wallSecs","throughput","predictedThroughput","accuracyPct",
              "meanMs","p50Ms","p95Ms","p99Ms","minMs","maxMs",
              "csvFile"));
          bw.write("\n");
        }
        String ts = LocalDateTime.now().toString();
        String line = String.join(",",
            ts, runId,
            esc(cfg.wsBase), String.valueOf(totalMessages), String.valueOf(cfg.rooms),
            String.valueOf(cfg.senders), String.valueOf(cfg.queueCap),
            String.valueOf(cfg.ackTimeoutMs), String.valueOf(cfg.connectTimeoutSeconds),
            String.valueOf(assumedRttMs), String.valueOf(cfg.connectionOverheadMs),
            String.valueOf(ok), String.valueOf(fail), String.valueOf(sendFailAfter5),
            String.valueOf(retries), String.valueOf(connections), String.valueOf(reconnects),
            String.format(java.util.Locale.US, "%.3f", wallSecs),
            String.format(java.util.Locale.US, "%.1f", throughput),
            String.format(java.util.Locale.US, "%.1f", predicted),
            String.format(java.util.Locale.US, "%.1f", acc),
            String.format(java.util.Locale.US, "%.2f", s.meanMs()),
            String.valueOf(s.p50Ms()), String.valueOf(s.p95Ms()), String.valueOf(s.p99Ms()),
            String.valueOf(s.minMs()), String.valueOf(s.maxMs()),
            esc(csvFilePath));
        bw.write(line);
        bw.write("\n");
      }
    } catch (Exception ignored) {}
  }
}