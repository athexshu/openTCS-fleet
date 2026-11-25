package org.opentcs.kernel.extensions.orderdatabase;

import static java.util.Objects.requireNonNull;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes queued order changes to the database on its own thread.
 * <p>
 * The kernel only adds changes to the queue, which never blocks. While the database is slow or
 * unreachable, changes wait in the queue; once the queue is full, further changes are dropped.
 * </p>
 */
public class OrderDatabaseWorker {

  /**
   * This class's logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(OrderDatabaseWorker.class);
  /**
   * How long to wait after the first failed attempt to reach the database.
   */
  private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(5);
  /**
   * The longest wait between attempts to reach the database.
   */
  private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(60);
  /**
   * The minimum time between two warnings of the same kind.
   */
  private static final long WARNING_INTERVAL_MILLIS = 60_000;
  /**
   * How long to wait for a queued change before checking whether to stop.
   */
  private static final long POLL_TIMEOUT_MILLIS = 500;
  /**
   * How long to sleep at a time while waiting before a retry.
   */
  private static final long SLEEP_SLICE_MILLIS = 100;
  /**
   * The changes waiting to be written.
   */
  private final BlockingQueue<OrderChange> queue;
  /**
   * Writes the changes.
   */
  private final OrderDatabaseWriter writer;
  /**
   * The number of changes dropped because the queue was full.
   */
  private final AtomicLong droppedChanges = new AtomicLong();
  /**
   * When the last warning about dropped changes was logged.
   */
  private final AtomicLong lastDropWarningMillis = new AtomicLong();
  /**
   * Whether the worker is running and accepting new changes.
   */
  private volatile boolean running;
  /**
   * After a stop request: until when queued changes are still written.
   */
  private volatile long stopDeadlineNanos;
  /**
   * The thread writing the changes, or {@code null} if not started.
   */
  private Thread thread;
  /**
   * How long to wait before the next attempt to reach the database. Used by the worker thread.
   */
  private Duration retryDelay = FIRST_RETRY_DELAY;
  /**
   * When the last warning about an unreachable database was logged. Used by the worker thread.
   */
  private long lastConnectWarningMillis;
  /**
   * Whether the worker thread is connected to the database. Used by the worker thread.
   */
  private boolean connected;

  /**
   * Creates a new instance.
   *
   * @param queueCapacity The maximum number of changes waiting to be written.
   * @param writer Writes the changes.
   */
  public OrderDatabaseWorker(int queueCapacity, OrderDatabaseWriter writer) {
    this.queue = new ArrayBlockingQueue<>(queueCapacity);
    this.writer = requireNonNull(writer, "writer");
  }

  /**
   * Starts the thread writing the queued changes.
   */
  public synchronized void start() {
    if (thread != null) {
      return;
    }
    running = true;
    thread = new Thread(this::run, "orderDatabaseWriter");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Adds a change to the queue without blocking.
   *
   * @param change The change.
   * @return {@code true} if the change was queued, {@code false} if the queue was full and the
   * change was dropped.
   */
  public boolean offer(OrderChange change) {
    requireNonNull(change, "change");
    if (queue.offer(change)) {
      return true;
    }

    long dropped = droppedChanges.incrementAndGet();
    long now = System.currentTimeMillis();
    long lastWarning = lastDropWarningMillis.get();
    if (now - lastWarning >= WARNING_INTERVAL_MILLIS
        && lastDropWarningMillis.compareAndSet(lastWarning, now)) {
      LOG.warn(
          "Order database: queue full, {} transport order changes dropped so far.",
          dropped
      );
    }
    return false;
  }

  /**
   * Returns the number of changes dropped because the queue was full.
   *
   * @return The number of dropped changes.
   */
  public long getDroppedChanges() {
    return droppedChanges.get();
  }

  /**
   * Returns the number of changes waiting to be written.
   *
   * @return The number of queued changes.
   */
  public int getQueuedChanges() {
    return queue.size();
  }

  /**
   * Stops the thread. Queued changes are still written until the given time has passed.
   *
   * @param flushTimeout How long to keep writing queued changes.
   */
  public void stop(Duration flushTimeout) {
    requireNonNull(flushTimeout, "flushTimeout");
    Thread workerThread;
    synchronized (this) {
      workerThread = thread;
      thread = null;
    }
    if (workerThread == null) {
      return;
    }

    stopDeadlineNanos = System.nanoTime() + flushTimeout.toNanos();
    running = false;
    try {
      workerThread.join(flushTimeout.toMillis() + 2_000);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (workerThread.isAlive()) {
      workerThread.interrupt();
    }
    if (!queue.isEmpty()) {
      LOG.warn(
          "Order database: {} transport order changes were not written before stopping.",
          queue.size()
      );
    }
  }

  private void run() {
    try {
      // Connect right away, so a wrong setting shows up in the log when the kernel starts.
      ensureConnected(queue.size());
      while (running || (!queue.isEmpty() && !stopDeadlinePassed())) {
        OrderChange change = queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (change != null && !writeWithRetry(change)) {
          // Stopping: keep the change counted as not written.
          queue.offer(change);
          break;
        }
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    finally {
      writer.close();
    }
  }

  /**
   * Writes one change, retrying while the database is unreachable.
   *
   * @param change The change.
   * @return {@code false} if the worker is stopping and the change could not be written.
   */
  private boolean writeWithRetry(OrderChange change) {
    while (true) {
      // The change being written counts as waiting, too.
      if (!ensureConnected(queue.size() + 1)) {
        if (!waitBeforeRetry()) {
          return false;
        }
        continue;
      }

      try {
        writer.write(change);
        return true;
      }
      catch (SQLException e) {
        if (!OrderDatabaseWriter.isConnectionProblem(e)) {
          LOG.error(
              "Order database: could not write a change of transport order {}; skipping it.",
              change.order().getName(),
              e
          );
          return true;
        }
        LOG.warn("Order database: connection lost ({}). Retrying.", e.getMessage());
        writer.close();
        connected = false;
        if (!waitBeforeRetry()) {
          return false;
        }
      }
      catch (RuntimeException e) {
        LOG.error(
            "Order database: unexpected error with a change of transport order {}; skipping it.",
            change.order().getName(),
            e
        );
        return true;
      }
    }
  }

  private boolean ensureConnected(int waitingChanges) {
    try {
      writer.connect();
      if (!connected) {
        connected = true;
        retryDelay = FIRST_RETRY_DELAY;
        LOG.info("Order database: connected.");
      }
      return true;
    }
    catch (SQLException | RuntimeException e) {
      connected = false;
      long now = System.currentTimeMillis();
      if (now - lastConnectWarningMillis >= WARNING_INTERVAL_MILLIS) {
        lastConnectWarningMillis = now;
        LOG.warn(
            "Order database: cannot connect ({}). {} changes are waiting and will be written "
                + "once the database is reachable.",
            e.getMessage(),
            waitingChanges
        );
      }
      return false;
    }
  }

  private boolean waitBeforeRetry() {
    long waitUntilNanos = System.nanoTime() + retryDelay.toNanos();
    Duration nextDelay = retryDelay.multipliedBy(2);
    retryDelay = nextDelay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : nextDelay;

    while (System.nanoTime() - waitUntilNanos < 0) {
      if (!running && stopDeadlinePassed()) {
        return false;
      }
      try {
        Thread.sleep(SLEEP_SLICE_MILLIS);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }

  private boolean stopDeadlinePassed() {
    return System.nanoTime() - stopDeadlineNanos >= 0;
  }
}
