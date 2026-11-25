package org.opentcs.kernel.extensions.orderdatabase;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.opentcs.data.order.TransportOrder;

/**
 * One change of a transport order, as reported by the kernel.
 *
 * @param time When the kernel reported the change.
 * @param type The kind of change.
 * @param previous The order before the change, or {@code null} if there is none.
 * @param order The order after the change. For a removal, its last state.
 */
public record OrderChange(
    Instant time,
    Type type,
    TransportOrder previous,
    TransportOrder order
) {

  /**
   * Creates a new instance.
   *
   * @param time When the kernel reported the change.
   * @param type The kind of change.
   * @param previous The order before the change, or {@code null} if there is none.
   * @param order The order after the change. For a removal, its last state.
   */
  public OrderChange {
    requireNonNull(time, "time");
    requireNonNull(type, "type");
    requireNonNull(order, "order");
  }

  /**
   * The kind of change.
   */
  public enum Type {
    /**
     * The order already existed when the extension started.
     */
    SNAPSHOT,
    /**
     * The order was created.
     */
    CREATED,
    /**
     * The order was modified.
     */
    MODIFIED,
    /**
     * The kernel removed the order. Finished orders are removed after a while.
     */
    REMOVED
  }
}
