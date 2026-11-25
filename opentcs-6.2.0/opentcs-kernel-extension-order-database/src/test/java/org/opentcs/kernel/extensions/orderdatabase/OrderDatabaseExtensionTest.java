package org.opentcs.kernel.extensions.orderdatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentcs.components.kernel.services.TCSObjectService;
import org.opentcs.data.TCSObjectEvent;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.util.event.EventSource;

/**
 * Tests for {@link OrderDatabaseExtension}.
 */
class OrderDatabaseExtensionTest {

  private final Instant now = Instant.parse("2026-11-25T10:00:00Z");

  @Test
  void turnsTransportOrderEventsIntoChanges() {
    TransportOrder created = TestOrders.order("TOrder-1");
    TransportOrder modified = created.withState(TransportOrder.State.ACTIVE);

    OrderChange creation = OrderDatabaseExtension.toOrderChange(
        new TCSObjectEvent(created, null, TCSObjectEvent.Type.OBJECT_CREATED),
        now
    );
    assertThat(creation.type()).isEqualTo(OrderChange.Type.CREATED);
    assertThat(creation.time()).isEqualTo(now);
    assertThat(creation.previous()).isNull();
    assertThat(creation.order()).isSameAs(created);

    OrderChange modification = OrderDatabaseExtension.toOrderChange(
        new TCSObjectEvent(modified, created, TCSObjectEvent.Type.OBJECT_MODIFIED),
        now
    );
    assertThat(modification.type()).isEqualTo(OrderChange.Type.MODIFIED);
    assertThat(modification.previous()).isSameAs(created);
    assertThat(modification.order()).isSameAs(modified);

    OrderChange removal = OrderDatabaseExtension.toOrderChange(
        new TCSObjectEvent(null, modified, TCSObjectEvent.Type.OBJECT_REMOVED),
        now
    );
    assertThat(removal.type()).isEqualTo(OrderChange.Type.REMOVED);
    assertThat(removal.order()).isSameAs(modified);
  }

  @Test
  void ignoresOtherEvents() {
    TCSObjectEvent vehicleEvent = new TCSObjectEvent(
        TestOrders.VEHICLE,
        TestOrders.VEHICLE,
        TCSObjectEvent.Type.OBJECT_MODIFIED
    );

    assertThat(OrderDatabaseExtension.toOrderChange(vehicleEvent, now)).isNull();
    assertThat(OrderDatabaseExtension.toOrderChange("not an object event", now)).isNull();
  }

  @Test
  void subscribesWhileRunning() {
    EventSource eventSource = mock(EventSource.class);
    TCSObjectService objectService = mock(TCSObjectService.class);
    when(objectService.fetchObjects(TransportOrder.class)).thenReturn(Set.of());
    OrderDatabaseExtension extension = new OrderDatabaseExtension(
        new OrderDatabaseSettings("jdbc:unknown:test", "user", "", 10),
        eventSource,
        objectService
    );

    extension.initialize();
    assertThat(extension.isInitialized()).isTrue();
    verify(eventSource).subscribe(extension);

    extension.terminate();
    assertThat(extension.isInitialized()).isFalse();
    verify(eventSource).unsubscribe(extension);
  }
}
