package org.opentcs.kernel.extensions.orderdatabase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.opentcs.data.ObjectHistory;
import org.opentcs.data.order.DriveOrder;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.data.order.TransportOrderHistoryCodes;

/**
 * Tests for {@link OrderRowMapper}.
 */
class OrderRowMapperTest {

  private final OrderRowMapper mapper = new OrderRowMapper();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void mapsFieldsTimesAndJson()
      throws Exception {
    Instant created = Instant.parse("2026-11-25T10:00:00Z");
    Instant finished = Instant.parse("2026-11-25T10:05:00Z");
    DriveOrder driveOrder = TestOrders.driveOrder()
        .withRoute(TestOrders.route(1500, "P1", "P2"))
        .withState(DriveOrder.State.FINISHED);
    TransportOrder order = TestOrders.order("TOrder-1", driveOrder)
        .withType("Transport")
        .withState(TransportOrder.State.FINISHED)
        .withCreationTime(created)
        .withFinishedTime(finished)
        .withProcessingVehicle(TestOrders.VEHICLE.getReference())
        .withProperty("wmsTask", "4711")
        .withHistoryEntry(new ObjectHistory.Entry(created, "tcs:history:orderCreated"));

    OrderRow row = mapper.toRow(order);

    assertThat(row.name()).isEqualTo("TOrder-1");
    assertThat(row.type()).isEqualTo("Transport");
    assertThat(row.state()).isEqualTo("FINISHED");
    assertThat(row.intendedVehicle()).isNull();
    assertThat(row.processingVehicle()).isEqualTo("Vehicle-01");
    assertThat(row.creationTime()).isEqualTo(created.atOffset(ZoneOffset.UTC));
    assertThat(row.finishedTime()).isEqualTo(finished.atOffset(ZoneOffset.UTC));
    // withState(FINISHED) adds the kernel's "reached final state" history entry.
    assertThat(row.finalStateTime()).isNotNull();
    assertThat(row.deadline()).isNull();
    assertThat(json.readTree(row.propertiesJson()).get("wmsTask").asText()).isEqualTo("4711");
    assertThat(row.driveOrderStatesJson()).isEqualTo("[\"FINISHED\"]");

    JsonNode drive = json.readTree(row.driveOrdersJson()).get(0);
    assertThat(drive.get("destination").asText()).isEqualTo("Loc-1");
    assertThat(drive.get("operation").asText()).isEqualTo("Load cargo");
    assertThat(drive.get("state").asText()).isEqualTo("FINISHED");
    assertThat(drive.get("route").get("costs").asLong()).isEqualTo(1500);
    assertThat(drive.get("route").get("steps").get(0).get("path").asText())
        .isEqualTo("P1 --- P2");

    // The TransportOrder constructor adds its own history entry first; ours is the last one.
    JsonNode historyEntries = json.readTree(row.historyJson());
    JsonNode history = historyEntries.get(historyEntries.size() - 1);
    assertThat(history.get("event").asText()).isEqualTo("tcs:history:orderCreated");
    assertThat(history.get("time").asText()).isEqualTo("2026-11-25T10:00:00Z");
  }

  @Test
  void unsetValuesAreNullOrEmpty()
      throws Exception {
    OrderRow row = mapper.toRow(TestOrders.order("TOrder-2"));

    assertThat(row.creationTime()).isNull();
    assertThat(row.finishedTime()).isNull();
    assertThat(row.finalStateTime()).isNull();
    assertThat(row.deadline()).isNull();
    assertThat(row.currentDriveOrderIndex()).isEqualTo(-1);
    assertThat(row.propertiesJson()).isEqualTo("{}");
    assertThat(row.dependenciesJson()).isEqualTo("[]");
    assertThat(json.readTree(row.driveOrdersJson()).get(0).get("route").isNull()).isTrue();
  }

  @Test
  void failedOrderHasFinalStateTimeButNoFinishedTime() {
    Instant failedAt = Instant.parse("2026-11-25T10:07:00Z");
    TransportOrder failed = TestOrders.order("TOrder-6")
        .withState(TransportOrder.State.FAILED)
        .withHistoryEntry(
            new ObjectHistory.Entry(failedAt, TransportOrderHistoryCodes.ORDER_REACHED_FINAL_STATE)
        );

    OrderRow row = mapper.toRow(failed);

    assertThat(row.state()).isEqualTo("FAILED");
    assertThat(row.finishedTime()).isNull();
    assertThat(row.finalStateTime()).isEqualTo(failedAt.atOffset(ZoneOffset.UTC));
  }

  @Test
  void describesAssignmentAndFailedDriveOrder() {
    TransportOrder dispatchable = TestOrders.order("TOrder-3")
        .withState(TransportOrder.State.DISPATCHABLE);
    TransportOrder assigned = dispatchable
        .withState(TransportOrder.State.BEING_PROCESSED)
        .withProcessingVehicle(TestOrders.VEHICLE.getReference())
        .withCurrentDriveOrderIndex(0)
        .withCurrentDriveOrderState(DriveOrder.State.TRAVELLING);
    TransportOrder failed = assigned.withCurrentDriveOrderState(DriveOrder.State.FAILED);

    assertThat(mapper.describeChanges(dispatchable, assigned)).isEqualTo(
        "state: DISPATCHABLE -> BEING_PROCESSED; "
            + "processingVehicle: - -> Vehicle-01; "
            + "currentDriveOrder: -1 -> 0; "
            + "driveOrder 0 (Loc-1, Load cargo): PRISTINE -> TRAVELLING; "
            // withProcessingVehicle() adds this history entry, like the kernel does.
            + "history: tcs:history:orderProcVehicleChanged (Vehicle-01)"
    );
    assertThat(mapper.describeChanges(assigned, failed))
        .isEqualTo("driveOrder 0 (Loc-1, Load cargo): TRAVELLING -> FAILED");
  }

  @Test
  void describesPropertiesHistoryAndRoutes() {
    TransportOrder before = TestOrders.order("TOrder-4");
    TransportOrder after = TestOrders.order(
        "TOrder-4",
        TestOrders.driveOrder().withRoute(TestOrders.route(800, "P1", "P2", "P3"))
    )
        .withProperty("wmsTask", "4711")
        .withHistoryEntry(
            new ObjectHistory.Entry(
                "tcs:history:orderAssignedToVehicle",
                TestOrders.VEHICLE.getReference()
            )
        );

    assertThat(mapper.describeChanges(before, after)).isEqualTo(
        "driveOrder 0 (Loc-1, Load cargo) route: - -> 2 steps to P3, costs 800; "
            + "property wmsTask: - -> 4711; "
            + "history: tcs:history:orderAssignedToVehicle (Vehicle-01)"
    );
  }

  @Test
  void describesNothingForRouteStepProgress() {
    TransportOrder atFirstStep = TestOrders.order("TOrder-5").withCurrentRouteStepIndex(0);

    assertThat(mapper.describeChanges(atFirstStep, atFirstStep.withCurrentRouteStepIndex(1)))
        .isEmpty();
  }
}
