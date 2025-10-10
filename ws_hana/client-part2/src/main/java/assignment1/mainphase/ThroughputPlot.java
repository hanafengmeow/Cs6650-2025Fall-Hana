package assignment1.mainphase;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.markers.Circle;
import org.knowm.xchart.style.markers.None;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

final class ThroughputPlot {
  private ThroughputPlot() {}

  static void writePng(MainConfig cfg, String runId, MetricsRecorder.Summary s,
                       long total, double wallSecs) {
    try {
      Path dir = Paths.get(cfg.chartDir);
      Files.createDirectories(dir);

      String fileName = String.format(
          "throughput_%s_S%d_Q%d_TO%ds.png",
          runId, cfg.senders, cfg.queueCap, cfg.ackTimeoutMs / 1000);
      Path out = dir.resolve(fileName);

      ArrayList<Double> x = new ArrayList<>();
      ArrayList<Double> y = new ArrayList<>();
      for (Map.Entry<Long, Long> e : s.bucketCounts().entrySet()) {
        double startSec = e.getKey() * cfg.bucketSec;
        double mps = e.getValue() / (double) cfg.bucketSec;
        x.add(startSec);
        y.add(mps);
      }

      boolean singlePoint = (x.size() == 1);
      if (singlePoint) {
        x.add(x.get(0) + cfg.bucketSec);
        y.add(y.get(0));
      }

      double avgMps = (wallSecs > 0) ? total / wallSecs : 0.0;
      String title = String.format(Locale.US,
          "Throughput over time | total=%d rooms=%d senders=%d queue=%d ackTO=%ds bucket=%ds wall=%.1fs avg=%.1f msg/s",
          total, cfg.rooms, cfg.senders, cfg.queueCap, cfg.ackTimeoutMs / 1000, cfg.bucketSec, wallSecs, avgMps);

      XYChart chart = new XYChartBuilder()
          .width(1000).height(520)
          .title(title)
          .xAxisTitle("Seconds")
          .yAxisTitle("Messages/sec")
          .build();

      chart.getStyler().setLegendVisible(false);
      var series = chart.addSeries("throughput", x, y);
      series.setMarker(singlePoint ? new Circle() : new None());

      BitmapEncoder.saveBitmap(chart, out.toString(), BitmapEncoder.BitmapFormat.PNG);
      System.out.println("Chart written to: " + out);
    } catch (Exception ignored) {}
  }
}