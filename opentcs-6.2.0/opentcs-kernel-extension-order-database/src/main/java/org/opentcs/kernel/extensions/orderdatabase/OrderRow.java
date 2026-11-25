package org.opentcs.kernel.extensions.orderdatabase;

import java.time.OffsetDateTime;

/**
 * The column values describing one transport order in the database.
 *
 * @param name The order's name.
 * @param type The order's type.
 * @param state The order's state.
 * @param intendedVehicle The intended vehicle's name, or {@code null}.
 * @param processingVehicle The processing vehicle's name, or {@code null}.
 * @param creationTime When the order was created, or {@code null} if not set.
 * @param finishedTime When the order was finished, or {@code null} if not set. openTCS sets
 * this only for the state FINISHED.
 * @param finalStateTime When the order reached a final state (FINISHED, FAILED or UNROUTABLE),
 * or {@code null} if it hasn't.
 * @param deadline The order's deadline, or {@code null} if not set.
 * @param dispensable Whether the order is dispensable.
 * @param wrappingSequence The wrapping order sequence's name, or {@code null}.
 * @param peripheralReservationToken The peripheral reservation token, or {@code null}.
 * @param currentDriveOrderIndex The index of the current drive order, or -1.
 * @param currentRouteStepIndex The index of the current route step, or -1.
 * @param propertiesJson The order's properties as a JSON object.
 * @param dependenciesJson The names of the orders this one depends on, as a JSON array.
 * @param driveOrdersJson The drive orders, with state and route, as a JSON array.
 * @param historyJson The order's history entries as a JSON array.
 * @param driveOrderStatesJson The drive orders' states as a JSON array.
 */
public record OrderRow(
    String name,
    String type,
    String state,
    String intendedVehicle,
    String processingVehicle,
    OffsetDateTime creationTime,
    OffsetDateTime finishedTime,
    OffsetDateTime finalStateTime,
    OffsetDateTime deadline,
    boolean dispensable,
    String wrappingSequence,
    String peripheralReservationToken,
    int currentDriveOrderIndex,
    int currentRouteStepIndex,
    String propertiesJson,
    String dependenciesJson,
    String driveOrdersJson,
    String historyJson,
    String driveOrderStatesJson
) {
}
