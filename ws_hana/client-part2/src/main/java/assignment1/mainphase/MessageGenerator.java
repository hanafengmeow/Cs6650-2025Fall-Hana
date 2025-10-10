package assignment1.mainphase;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class MessageGenerator implements Runnable {
  private final BlockingQueue<Outbound> outQ;
  private final long total;
  private final int rooms;
  private final Gson gson = new Gson();

  public MessageGenerator(BlockingQueue<Outbound> outQ, long total, int rooms) {
    this.outQ = outQ;
    this.total = total;
    this.rooms = rooms;
  }

  @Override public void run() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (long i = 0; i < total; i++) {
      int uid = 1 + rnd.nextInt(100_000);
      String userId = Integer.toString(uid);

      int p = rnd.nextInt(100);
      String mt = (p < 90) ? "TEXT" : (p < 95 ? "JOIN" : "LEAVE");

      String msg = MessagePool.POOL[rnd.nextInt(MessagePool.POOL.length)];
      Message m = new Message();
      m.userId = userId;
      m.username = "user" + userId;
      m.message = msg;
      m.timestamp = Instant.now().toString();
      m.messageType = mt;

      int roomId = 1 + rnd.nextInt(rooms);
      String json = gson.toJson(m);

      try {
        outQ.put(new Outbound(roomId, json, mt));
      } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
    }
  }

  public record Outbound(int roomId, String json, String messageType) {}
}