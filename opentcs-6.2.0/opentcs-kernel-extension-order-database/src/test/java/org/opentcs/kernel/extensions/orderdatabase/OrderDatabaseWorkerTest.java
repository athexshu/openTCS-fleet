package org.opentcs.kernel.extensions.orderdatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OrderDatabaseWorker}, with a mocked writer.
 */
class OrderDatabaseWorkerTest {

  private final OrderDatabaseWriter writer = mock(OrderDatabaseWriter.class);

  @Test
  void dropsChangesWhenTheQueueIsFull() {
    OrderDatabaseWorker worker = new OrderDatabaseWorker(1, writer);

    assertThat(worker.offer(change("TOrder-1"))).isTrue();
    assertThat(worker.offer(change("TOrder-2"))).isFalse();
    assertThat(worker.getDroppedChanges()).isEqualTo(1);
    assertThat(worker.getQueuedChanges()).isEqualTo(1);
  }

  @Test
  void writesQueuedChanges()
      throws SQLException {
    OrderDatabaseWorker worker = new OrderDatabaseWorker(10, writer);
    worker.start();

    worker.offer(change("TOrder-1"));
    worker.offer(change("TOrder-2"));

    verify(writer, timeout(5_000).times(2)).write(any(OrderChange.class));
    worker.stop(Duration.ofSeconds(1));
    verify(writer, timeout(5_000)).close();
  }

  @Test
  void skipsAChangeWithBadDataAndContinues()
      throws SQLException {
    doThrow(new SQLException("value too long", "22001"))
        .doNothing()
        .when(writer).write(any(OrderChange.class));
    OrderDatabaseWorker worker = new OrderDatabaseWorker(10, writer);
    worker.start();

    worker.offer(change("TOrder-1"));
    worker.offer(change("TOrder-2"));

    verify(writer, timeout(5_000).times(2)).write(any(OrderChange.class));
    worker.stop(Duration.ofSeconds(1));
  }

  @Test
  void keepsChangesWhileTheDatabaseIsUnreachableAndStopsInTime()
      throws SQLException {
    doThrow(new SQLException("Connection refused", "08001")).when(writer).connect();
    OrderDatabaseWorker worker = new OrderDatabaseWorker(10, writer);
    worker.start();

    worker.offer(change("TOrder-1"));
    // Once at start, once for the change.
    verify(writer, timeout(5_000).times(2)).connect();

    long stopStart = System.nanoTime();
    worker.stop(Duration.ofMillis(300));

    assertThat(Duration.ofNanos(System.nanoTime() - stopStart)).isLessThan(Duration.ofSeconds(5));
    assertThat(worker.getQueuedChanges()).isEqualTo(1);
    verify(writer, never()).write(any(OrderChange.class));
  }

  private OrderChange change(String orderName) {
    return new OrderChange(
        Instant.parse("2026-11-25T10:00:00Z"),
        OrderChange.Type.CREATED,
        null,
        TestOrders.order(orderName)
    );
  }
}
