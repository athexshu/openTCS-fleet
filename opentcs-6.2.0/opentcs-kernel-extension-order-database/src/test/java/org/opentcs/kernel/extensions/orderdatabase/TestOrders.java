package org.opentcs.kernel.extensions.orderdatabase;

import java.util.ArrayList;
import java.util.List;
import org.opentcs.data.model.Location;
import org.opentcs.data.model.LocationType;
import org.opentcs.data.model.Path;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.DriveOrder;
import org.opentcs.data.order.Route;
import org.opentcs.data.order.TransportOrder;

/**
 * Builds orders for the tests.
 */
final class TestOrders {

  /**
   * The destination of the test orders.
   */
  static final Location LOCATION = new Location("Loc-1", new LocationType("LT").getReference());
  /**
   * A vehicle processing test orders.
   */
  static final Vehicle VEHICLE = new Vehicle("Vehicle-01");

  private TestOrders() {
  }

  /**
   * Returns a new drive order to {@link #LOCATION} with the operation "Load cargo".
   */
  static DriveOrder driveOrder() {
    return new DriveOrder(
        new DriveOrder.Destination(LOCATION.getReference()).withOperation("Load cargo")
    );
  }

  /**
   * Returns a new order with the given drive orders.
   */
  static TransportOrder order(String name, DriveOrder... driveOrders) {
    return new TransportOrder(name, List.of(driveOrders));
  }

  /**
   * Returns a new order with one drive order to {@link #LOCATION}.
   */
  static TransportOrder order(String name) {
    return order(name, driveOrder());
  }

  /**
   * Returns a route through the given points.
   */
  static Route route(long costs, String... pointNames) {
    List<Route.Step> steps = new ArrayList<>();
    for (int i = 1; i < pointNames.length; i++) {
      Point source = new Point(pointNames[i - 1]);
      Point destination = new Point(pointNames[i]);
      Path path = new Path(
          source.getName() + " --- " + destination.getName(),
          source.getReference(),
          destination.getReference()
      );
      steps.add(
          new Route.Step(path, source, destination, Vehicle.Orientation.FORWARD, i - 1, true, null)
      );
    }
    return new Route(steps, costs);
  }
}
