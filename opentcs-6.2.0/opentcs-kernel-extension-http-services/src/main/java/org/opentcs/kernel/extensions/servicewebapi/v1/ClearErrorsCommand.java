package org.opentcs.kernel.extensions.servicewebapi.v1;

import org.opentcs.drivers.vehicle.AdapterCommand;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;

public class ClearErrorsCommand
    implements
      AdapterCommand {

  public ClearErrorsCommand() {
    // No initialization needed
  }

  @Override
  public void execute(VehicleCommAdapter adapter) {
    adapter.getProcessModel().clearErrorState();
  }
}
