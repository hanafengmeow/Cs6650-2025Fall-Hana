package assignment1.mainphase;

import assignment1.mainphase.MessageGenerator.Outbound;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class SenderWorker implements Runnable {
  private final BlockingQueue<Outbound> q;
  private final WebSocketManager pool;
  private final AtomicLong fail, retries, sendFailAfter5;

  SenderWorker(BlockingQueue<Outbound> q, WebSocketManager pool,
      AtomicLong fail, AtomicLong retries, AtomicLong sendFailAfter5) {
    this.q = q;
    this.pool = pool;
    this.fail = fail;
    this.retries = retries;
    this.sendFailAfter5 = sendFailAfter5;
  }

  @Override public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      Outbound ob;
      try {
        ob = q.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        break;
      }
      if (ob == null) continue;

      long backoff = 10;
      boolean sent = false;

      for (int r = 0; r < 5 && !sent; r++) {
        try {
          WebSocketManager.BasicClient c = pool.get(ob.roomId());
          long start = System.nanoTime();
          long sendEpochMs = System.currentTimeMillis();
          c.registerSend(start, sendEpochMs, ob.roomId(), ob.messageType());
          c.safeSend(ob.json());
          sent = true;
        } catch (RuntimeException re) {
          if (r > 0) retries.incrementAndGet();
          if (r == 4) {
            fail.incrementAndGet();
            sendFailAfter5.incrementAndGet();
            break;
          }
          sleep(backoff + ThreadLocalRandom.current().nextInt(20));
          backoff = Math.min(200, backoff * 2);
        } catch (Exception e) {
          if (r > 0) retries.incrementAndGet();
          if (r == 4) {
            fail.incrementAndGet();
            sendFailAfter5.incrementAndGet();
          } else {
            sleep(backoff);
          }
        }
      }
    }
  }

  private static void sleep(long ms) {
    try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
  }
}