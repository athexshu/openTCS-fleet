package org.opentcs.kernel.extensions.orderdatabase;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.opentcs.data.ObjectHistory;
import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.order.DriveOrder;
import org.opentcs.data.order.Route;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.data.order.TransportOrderHistoryCodes;

/**
 * Maps transport orders to the column values stored in the database.
 */
public class OrderRowMapper {

  /**
   * Shown for a value that is not set.
   */
  private static final String NOT_SET = "-";
  /**
   * The latest time PostgreSQL can store comfortably. Later times count as "not set".
   */
  private static final Instant LATEST_TIME = Instant.parse("9999-12-31T23:59:59Z");
  /**
   * Creates the JSON nodes.
   */
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  /**
   * Creates a new instance.
   */
  public OrderRowMapper() {
  }

  /**
   * Returns the column values describing the given order.
   *
   * @param order The order.
   * @return The column values.
   */
  public OrderRow toRow(TransportOrder order) {
    requireNonNull(order, "order");
    return new OrderRow(
        order.getName(),
        order.getType(),
        order.getState().name(),
        nameOf(order.getIntendedVehicle()),
        nameOf(order.getProcessingVehicle()),
        toTimestamp(order.getCreationTime()),
        toTimestamp(order.getFinishedTime()),
        finalStateTime(order.getHistory()),
        toTimestamp(order.getDeadline()),
        order.isDispensable(),
        nameOf(order.getWrappingSequence()),
        order.getPeripheralReservationToken(),
        order.getCurrentDriveOrderIndex(),
        order.getCurrentRouteStepIndex(),
        propertiesJson(order.getProperties()).toString(),
        namesJson(names(order.getDependencies())).toString(),
        driveOrdersJson(order.getAllDriveOrders()).toString(),
        historyJson(order.getHistory()).toString(),
        driveOrderStatesJson(order.getAllDriveOrders()).toString()
    );
  }

  /**
   * Describes what changed between two states of an order, for example
   * {@code state: DISPATCHABLE -> BEING_PROCESSED}.
   * A change of the current route step alone is not described.
   *
   * @param previous The order before the change.
   * @param current The order after the change.
   * @return The changes separated by "; ", or an empty string if nothing described changed.
   */
  public String describeChanges(TransportOrder previous, TransportOrder current) {
    requireNonNull(previous, "previous");
    requireNonNull(current, "current");

    List<String> changes = new ArrayList<>();
    addIfChanged(changes, "state", previous.getState(), current.getState());
    addIfChanged(changes, "type", previous.getType(), current.getType());
    addIfChanged(
        changes,
        "intendedVehicle",
        nameOf(previous.getIntendedVehicle()),
        nameOf(current.getIntendedVehicle())
    );
    addIfChanged(
        changes,
        "processingVehicle",
        nameOf(previous.getProcessingVehicle()),
        nameOf(current.getProcessingVehicle())
    );
    addIfChanged(
        changes,
        "deadline",
        toTimestamp(previous.getDeadline()),
        toTimestamp(current.getDeadline())
    );
    addIfChanged(changes, "dispensable", previous.isDispensable(), current.isDispensable());
    addIfChanged(
        changes,
        "currentDriveOrder",
        previous.getCurrentDriveOrderIndex(),
        current.getCurrentDriveOrderIndex()
    );
    describeDriveOrderChanges(changes, previous.getAllDriveOrders(), current.getAllDriveOrders());
    describePropertyChanges(changes, previous.getProperties(), current.getProperties());
    addIfChanged(
        changes,
        "dependencies",
        names(previous.getDependencies()),
        names(current.getDependencies())
    );
    describeNewHistoryEntries(changes, previous.getHistory(), current.getHistory());
    return String.join("; ", changes);
  }

  /**
   * Converts a kernel time to a database time.
   * openTCS marks a time that is not set with {@link Instant#EPOCH} or {@link Instant#MAX}.
   *
   * @param instant The kernel time.
   * @return The database time, or {@code null} if the time is not set.
   */
  public static OffsetDateTime toTimestamp(Instant instant) {
    if (instant == null || Instant.EPOCH.equals(instant) || instant.isAfter(LATEST_TIME)) {
      return null;
    }
    return instant.atOffset(ZoneOffset.UTC);
  }

  /**
   * Returns when the order reached a final state, taken from the kernel's own history entry.
   * openTCS sets the finished time only for FINISHED, but adds this entry for every final state.
   */
  private static OffsetDateTime finalStateTime(ObjectHistory history) {
    List<ObjectHistory.Entry> entries = history.getEntries();
    for (int i = entries.size() - 1; i >= 0; i--) {
      if (TransportOrderHistoryCodes.ORDER_REACHED_FINAL_STATE.equals(
          entries.get(i).getEventCode()
      )) {
        return toTimestamp(entries.get(i).getTimestamp());
      }
    }
    return null;
  }

  private static String nameOf(TCSObjectReference<?> reference) {
    return reference == null ? null : reference.getName();
  }

  private static Set<String> names(Set<? extends TCSObjectReference<?>> references) {
    Set<String> result = new TreeSet<>();
    for (TCSObjectReference<?> reference : references) {
      result.add(reference.getName());
    }
    return result;
  }

  private static ArrayNode namesJson(Set<String> names) {
    ArrayNode array = JSON.arrayNode();
    for (String name : names) {
      array.add(name);
    }
    return array;
  }

  private static ObjectNode propertiesJson(Map<String, String> properties) {
    ObjectNode node = JSON.objectNode();
    for (Map.Entry<String, String> property : new TreeMap<>(properties).entrySet()) {
      node.put(property.getKey(), property.getValue());
    }
    return node;
  }

  private static ArrayNode driveOrdersJson(List<DriveOrder> driveOrders) {
    ArrayNode array = JSON.arrayNode();
    for (int i = 0; i < driveOrders.size(); i++) {
      DriveOrder driveOrder = driveOrders.get(i);
      ObjectNode node = array.addObject();
      node.put("index", i);
      node.put("destination", driveOrder.getDestination().getDestination().getName());
      node.put("operation", driveOrder.getDestination().getOperation());
      node.put("state", driveOrder.getState().name());
      node.set("properties", propertiesJson(driveOrder.getDestination().getProperties()));
      node.set("route", routeJson(driveOrder.getRoute()));
    }
    return array;
  }

  private static JsonNode routeJson(Route route) {
    if (route == null) {
      return JSON.nullNode();
    }
    ObjectNode node = JSON.objectNode();
    node.put("costs", route.getCosts());
    ArrayNode steps = node.putArray("steps");
    for (Route.Step step : route.getSteps()) {
      ObjectNode stepNode = steps.addObject();
      stepNode.put("index", step.getRouteIndex());
      stepNode.put("path", step.getPath() == null ? null : step.getPath().getName());
      stepNode.put(
          "from",
          step.getSourcePoint() == null ? null : step.getSourcePoint().getName()
      );
      stepNode.put("to", step.getDestinationPoint().getName());
      stepNode.put("orientation", step.getVehicleOrientation().name());
    }
    return node;
  }

  private static ArrayNode historyJson(ObjectHistory history) {
    ArrayNode array = JSON.arrayNode();
    for (ObjectHistory.Entry entry : history.getEntries()) {
      ObjectNode node = array.addObject();
      node.put("time", entry.getTimestamp().toString());
      node.put("event", entry.getEventCode());
      node.put("supplement", supplementText(entry.getSupplement()));
    }
    return array;
  }

  private static String supplementText(Object supplement) {
    if (supplement == null) {
      return null;
    }
    if (supplement instanceof TCSObjectReference<?> reference) {
      return reference.getName();
    }
    String text = supplement.toString();
    return text.isEmpty() ? null : text;
  }

  private static ArrayNode driveOrderStatesJson(List<DriveOrder> driveOrders) {
    ArrayNode array = JSON.arrayNode();
    for (DriveOrder driveOrder : driveOrders) {
      array.add(driveOrder.getState().name());
    }
    return array;
  }

  private static void addIfChanged(
      List<String> changes,
      String field,
      Object before,
      Object after
  ) {
    if (!Objects.equals(before, after)) {
      changes.add(field + ": " + text(before) + " -> " + text(after));
    }
  }

  private static String text(Object value) {
    return value == null ? NOT_SET : value.toString();
  }

  private static void describeDriveOrderChanges(
      List<String> changes,
      List<DriveOrder> before,
      List<DriveOrder> after
  ) {
    if (before.size() != after.size()) {
      changes.add("driveOrders: " + before.size() + " -> " + after.size());
      return;
    }
    for (int i = 0; i < after.size(); i++) {
      DriveOrder previous = before.get(i);
      DriveOrder current = after.get(i);
      String label = "driveOrder " + i
          + " (" + current.getDestination().getDestination().getName()
          + ", " + current.getDestination().getOperation() + ")";
      if (previous.getState() != current.getState()) {
        changes.add(label + ": " + previous.getState() + " -> " + current.getState());
      }
      if (!routeSignature(previous.getRoute()).equals(routeSignature(current.getRoute()))) {
        changes.add(
            label + " route: " + routeSummary(previous.getRoute())
                + " -> " + routeSummary(current.getRoute())
        );
      }
    }
  }

  private static String routeSignature(Route route) {
    if (route == null) {
      return NOT_SET;
    }
    return route.getCosts() + ":" + route.getSteps().stream()
        .map(step -> step.getDestinationPoint().getName())
        .collect(Collectors.joining(","));
  }

  private static String routeSummary(Route route) {
    if (route == null) {
      return NOT_SET;
    }
    return route.getSteps().size() + " steps to " + route.getFinalDestinationPoint().getName()
        + ", costs " + route.getCosts();
  }

  private static void describePropertyChanges(
      List<String> changes,
      Map<String, String> before,
      Map<String, String> after
  ) {
    Set<String> keys = new TreeSet<>(before.keySet());
    keys.addAll(after.keySet());
    for (String key : keys) {
      addIfChanged(changes, "property " + key, before.get(key), after.get(key));
    }
  }

  private static void describeNewHistoryEntries(
      List<String> changes,
      ObjectHistory before,
      ObjectHistory after
  ) {
    List<ObjectHistory.Entry> previousEntries = before.getEntries();
    List<ObjectHistory.Entry> currentEntries = after.getEntries();
    for (int i = previousEntries.size(); i < currentEntries.size(); i++) {
      ObjectHistory.Entry entry = currentEntries.get(i);
      String supplement = supplementText(entry.getSupplement());
      changes.add(
          "history: " + entry.getEventCode() + (supplement == null ? "" : " (" + supplement + ")")
      );
    }
  }
}
