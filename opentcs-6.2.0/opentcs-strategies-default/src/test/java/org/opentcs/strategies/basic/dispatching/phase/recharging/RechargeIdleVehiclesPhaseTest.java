// SPDX-FileCopyrightText: The openTCS Authors
// SPDX-License-Identifier: MIT
package org.opentcs.strategies.basic.dispatching.phase.recharging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentcs.components.kernel.Router;
import org.opentcs.components.kernel.services.InternalTransportOrderService;
import org.opentcs.data.model.Vehicle;
import org.opentcs.strategies.basic.dispatching.DefaultDispatcherConfiguration;
import org.opentcs.strategies.basic.dispatching.TransportOrderUtil;
import org.opentcs.strategies.basic.dispatching.selection.candidates.CompositeAssignmentCandidateSelectionFilter;
import org.opentcs.strategies.basic.dispatching.selection.vehicles.CompositeRechargeVehicleSelectionFilter;

/**
 * Test for {@link RechargeIdleVehiclesPhase}.
 */
class RechargeIdleVehiclesPhaseTest {

  private InternalTransportOrderService orderService;
  private RechargePositionSupplier rechargePosSupplier;
  private CompositeRechargeVehicleSelectionFilter vehicleSelectionFilter;
  private DefaultDispatcherConfiguration configuration;
  private RechargeIdleVehiclesPhase rechargeIdleVehiclesPhase;
  private Vehicle degradedVehicle;

  @BeforeEach
  void setUp() {
    orderService = mock();
    rechargePosSupplier = mock();
    vehicleSelectionFilter = mock();
    configuration = mock();
    rechargeIdleVehiclesPhase = new RechargeIdleVehiclesPhase(
        orderService,
        rechargePosSupplier,
        mock(Router.class),
        mock(CompositeAssignmentCandidateSelectionFilter.class),
        vehicleSelectionFilter,
        mock(TransportOrderUtil.class),
        configuration
    );
    degradedVehicle = new Vehicle("V1").withEnergyLevel(50);

    given(configuration.rechargeIdleVehicles()).willReturn(true);
    given(vehicleSelectionFilter.apply(any())).willReturn(List.of());
    // An empty recharge sequence ends createRechargeOrder() before a transport order is created.
    given(rechargePosSupplier.findRechargeSequence(any())).willReturn(List.of());
  }

  @Test
  void skipRechargeOrderForLoadedVehicle() {
    Vehicle vehicle = degradedVehicle
        .withProperty("vehicle.loaded", "true");
    given(orderService.fetchObjects(Vehicle.class)).willReturn(Set.of(vehicle));

    rechargeIdleVehiclesPhase.run();

    verify(rechargePosSupplier, never()).findRechargeSequence(any());
    verify(orderService, never()).createTransportOrder(any());
  }

  @Test
  void skipRechargeOrderForLoadedVehicleWithCriticalEnergyLevel() {
    Vehicle vehicle = degradedVehicle
        .withEnergyLevel(20)
        .withProperty("vehicle.loaded", "true");
    given(orderService.fetchObjects(Vehicle.class)).willReturn(Set.of(vehicle));

    rechargeIdleVehiclesPhase.run();

    verify(rechargePosSupplier, never()).findRechargeSequence(any());
    verify(orderService, never()).createTransportOrder(any());
  }

  @Test
  void considerRechargeOrderForUnloadedVehicle() {
    Vehicle vehicle = degradedVehicle
        .withProperty("vehicle.loaded", "false");
    given(orderService.fetchObjects(Vehicle.class)).willReturn(Set.of(vehicle));

    rechargeIdleVehiclesPhase.run();

    verify(rechargePosSupplier, times(1)).findRechargeSequence(vehicle);
  }

  @Test
  void considerRechargeOrderForVehicleWithoutLoadedProperty() {
    given(orderService.fetchObjects(Vehicle.class)).willReturn(Set.of(degradedVehicle));

    rechargeIdleVehiclesPhase.run();

    verify(rechargePosSupplier, times(1)).findRechargeSequence(degradedVehicle);
  }
}
