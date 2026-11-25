/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.commadapter.vehicle.vda5050.v1_1;

import static java.util.Objects.requireNonNull;
import static org.opentcs.commadapter.vehicle.vda5050.v1_1.ObjectProperties.PROPKEY_VEHICLE_MOVEMENT_COMMAND_COMPLETED_CONDITION;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.opentcs.commadapter.vehicle.vda5050.common.MovementCommandCompletedCondition;
import org.opentcs.commadapter.vehicle.vda5050.common.PropertyExtractions;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.common.Action;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.order.Edge;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.order.Node;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.order.Order;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.state.ActionState;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.state.ActionStatus;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.state.EdgeState;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.state.NodeState;
import org.opentcs.commadapter.vehicle.vda5050.v1_1.message.state.State;
import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.MovementCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the progress of movement commands and reports back finished ones.
 */
public class MovementCommandManager {

  private static final Logger LOG = LoggerFactory.getLogger(MovementCommandManager.class);

  /**
   * A list of currently tracked orders.
   */
  private final Queue<OrderAssociation> trackedOrders = new ArrayDeque<>();
  /**
   * The movement command completed condition.
   */
  private final MovementCommandCompletedCondition completedCondition;
  /**
   * The name of the vehicle (for logging).
   */
  private final String vehicleName;

  /**
   * Construct a new MovementCommandManager.
   *
   * @param vehicle The vehicle to create the manager for.
   */
  @Inject
  public MovementCommandManager(
      @Assisted
      Vehicle vehicle
  ) {
    requireNonNull(vehicle, "vehicle");
    this.vehicleName = vehicle.getName();
    this.completedCondition = PropertyExtractions.getMovementCommandCompletedCondition(
        PROPKEY_VEHICLE_MOVEMENT_COMMAND_COMPLETED_CONDITION, vehicle
    ).orElse(MovementCommandCompletedCondition.EDGE_AND_NODE);
  }

  /**
   * Adds a movement command and an order to be tracked.
   *
   * @param orderAssociation The order association to track.
   */
  public void enqueue(
      @Nonnull
      OrderAssociation orderAssociation
  ) {
    requireNonNull(orderAssociation, "orderAssociation");

    trackedOrders.add(orderAssociation);
  }

  /**
   * Clears tracked order associations.
   */
  public void clear() {
    trackedOrders.clear();
  }

  /**
   * Notifies this instance about a new state reported by the vehicle.
   * Calls the respective callback function for any movement commands considered to be
   * completed.
   * <p>
   * A final movement command (i.e. the last one of a drive order) is considered completed once
   * all of its actions are in a final state ({@link ActionStatus#FINISHED} or
   * {@link ActionStatus#FAILED}). If any of these actions has failed, the command is reported via
   * {@code onFailed} instead of {@code onExecuted}.
   * </p>
   *
   * @param currentState The current state message.
   * @param onExecuted The callback for movement commands that have been executed successfully.
   * @param onFailed The callback for final movement commands with at least one failed action.
   */
  public void onStateMessage(
      @Nonnull
      State currentState,
      @Nonnull
      Consumer<MovementCommand> onExecuted,
      @Nonnull
      Consumer<MovementCommand> onFailed
  ) {
    requireNonNull(currentState, "currentState");
    requireNonNull(onExecuted, "onExecuted");
    requireNonNull(onFailed, "onFailed");

    if (!reportsOrderIdForCurrentDriveOrder(currentState)) {
      return;
    }

    Iterator<OrderAssociation> iter = trackedOrders.iterator();
    while (iter.hasNext()
        && checkForCompletionAndReport(iter.next(), currentState, onExecuted, onFailed)) {
      iter.remove();
    }
  }

  /**
   * Fails the current movement command and removes it from this instance.
   *
   * @param callback The callback for failed movement commands.
   */
  public void failCurrentCommand(
      @Nonnull
      Consumer<MovementCommand> callback
  ) {
    if (!trackedOrders.isEmpty()) {
      callback.accept(trackedOrders.poll().getCommand());
    }
    else {
      LOG.debug("No commands to fail");
    }
  }

  private boolean checkForCompletionAndReport(
      OrderAssociation association,
      State state,
      Consumer<MovementCommand> onExecuted,
      Consumer<MovementCommand> onFailed
  ) {
    if (!orderComplete(association, state)) {
      return false;
    }

    if (association.getCommand().isFinalMovement()) {
      LOG.info(
          "{}: Last step of order {} completed, action states: {}",
          vehicleName,
          association.getOrder().getOrderId(),
          describeActionStates(association.getOrder(), state)
      );
    }

    List<ActionState> failedActions = association.getCommand().isFinalMovement()
        ? failedActions(association.getOrder(), state)
        : List.of();
    if (failedActions.isEmpty()) {
      onExecuted.accept(association.getCommand());
    }
    else {
      for (ActionState failedAction : failedActions) {
        LOG.warn(
            "{}: Action failed in order {}: actionId={}, actionType={}, resultDescription={}",
            vehicleName,
            association.getOrder().getOrderId(),
            failedAction.getActionId(),
            failedAction.getActionType(),
            failedAction.getResultDescription()
        );
      }
      onFailed.accept(association.getCommand());
    }
    return true;
  }

  private boolean orderComplete(OrderAssociation assocation, State state) {
    return movementComplete(assocation, state)
        && (!assocation.getCommand().isFinalMovement()
            || actionsComplete(assocation.getOrder(), state));
  }

  private boolean movementComplete(OrderAssociation association, State state) {
    if (association.getCommand().isFinalMovement()) {
      return edgesComplete(association.getOrder(), state)
          && nodesComplete(association.getOrder(), state);
    }
    switch (completedCondition) {
      case EDGE:
        return edgesComplete(association.getOrder(), state);
      case EDGE_AND_NODE:
      default:
        return edgesComplete(association.getOrder(), state)
            && nodesComplete(association.getOrder(), state);
    }
  }

  private boolean edgesComplete(Order order, State state) {
    return order.getEdges().stream()
        .filter(edge -> edge.isReleased())
        .allMatch(edge -> edgeComplete(edge, state.getEdgeStates()));
  }

  private boolean nodesComplete(Order order, State state) {
    return order.getNodes().stream()
        .filter(node -> node.isReleased())
        .allMatch(node -> nodeComplete(node, state.getNodeStates()));
  }

  private boolean edgeComplete(Edge edge, List<EdgeState> edgeStates) {
    // An edge is complete if it is not in the list of edge states (any more).
    return edgeStates.stream().noneMatch(edgeState -> {
      return Objects.equals(edgeState.getEdgeId(), edge.getEdgeId())
          && Objects.equals(edgeState.getSequenceId(), edge.getSequenceId());
    });
  }

  private boolean nodeComplete(Node node, List<NodeState> nodeStates) {
    // A node is complete if it is not in the list of node states (any more).
    return nodeStates.stream().noneMatch(nodeState -> {
      return Objects.equals(nodeState.getNodeId(), node.getNodeId())
          && Objects.equals(nodeState.getSequenceId(), node.getSequenceId());
    });
  }

  private boolean actionsComplete(Order order, State state) {
    return actionsOf(order)
        .allMatch(action -> actionComplete(action, state.getActionStates()));
  }

  private List<ActionState> failedActions(Order order, State state) {
    return actionsOf(order)
        .flatMap(
            action -> state.getActionStates().stream()
                .filter(
                    actionState -> Objects.equals(actionState.getActionId(), action.getActionId())
                        && actionState.getActionStatus() == ActionStatus.FAILED
                )
        )
        .collect(Collectors.toList());
  }

  private Stream<Action> actionsOf(Order order) {
    return Stream.concat(
        order.getNodes().stream().flatMap(node -> node.getActions().stream()),
        order.getEdges().stream().flatMap(edge -> edge.getActions().stream())
    );
  }

  private String describeActionStates(Order order, State state) {
    return actionsOf(order)
        .map(action -> describeActionState(action, state.getActionStates()))
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private String describeActionState(Action action, List<ActionState> actionStates) {
    String status = actionStates.stream()
        .filter(actionState -> Objects.equals(actionState.getActionId(), action.getActionId()))
        .map(actionState -> String.valueOf(actionState.getActionStatus()))
        .findFirst()
        .orElse("not reported");
    return action.getActionId() + " (" + action.getActionType() + ")=" + status;
  }

  private boolean actionComplete(Action action, List<ActionState> actionStates) {
    return actionStates.stream()
        .anyMatch(actionState -> {
          return Objects.equals(actionState.getActionId(), action.getActionId())
              && isFinalActionStatus(actionState.getActionStatus());
        });
  }

  private boolean isFinalActionStatus(ActionStatus actionStatus) {
    return actionStatus == ActionStatus.FAILED
        || actionStatus == ActionStatus.FINISHED;
  }

  private boolean reportsOrderIdForCurrentDriveOrder(
      @Nonnull
      State state
  ) {
    return Objects.equals(
        state.getOrderId(),
        trackedOrders.stream()
            .findAny()
            .map(orderAssociation -> orderAssociation.getOrder().getOrderId())
            .orElse("")
    );
  }
}
