package org.opentcs.kernel.extensions.orderdatabase;

import static java.util.Objects.requireNonNull;

import jakarta.inject.Inject;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import org.opentcs.components.kernel.KernelExtension;
import org.opentcs.components.kernel.services.TCSObjectService;
import org.opentcs.customizations.ApplicationEventBus;
import org.opentcs.data.TCSObjectEvent;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.util.event.EventHandler;
import org.opentcs.util.event.EventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes every transport order change to a PostgreSQL database.
 * <p>
 * The kernel's event thread only puts changes into a queue. A separate thread writes them, so a
 * slow or unreachable database never holds up the kernel.
 * </p>
 */
public class OrderDatabaseExtension
    implements
      KernelExtension,
      EventHandler {

  /**
   * This class's logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(OrderDatabaseExtension.class);
  /**
   * How long queued changes are still written when the extension stops.
   */
  private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(5);
  /**
   * The database settings.
   */
  private final OrderDatabaseSettings settings;
  /**
   * Where the kernel publishes object changes.
   */
  private final EventSource eventSource;
  /**
   * Provides the orders existing when the extension starts.
   */
  private final TCSObjectService objectService;
  /**
   * Writes the changes, or {@code null} while the extension isn't running.
   */
  private volatile OrderDatabaseWorker worker;
  /**
   * Whether this extension is initialized.
   */
  private boolean initialized;

  /**
   * Creates a new instance.
   *
   * @param settings The database settings.
   * @param eventSource Where the kernel publishes object changes.
   * @param objectService Provides the orders existing when the extension starts.
   */
  @Inject
  public OrderDatabaseExtension(
      OrderDatabaseSettings settings,
      @ApplicationEventBus
      EventSource eventSource,
      TCSObjectService objectService
  ) {
    this.settings = requireNonNull(settings, "settings");
    this.eventSource = requireNonNull(eventSource, "eventSource");
    this.objectService = requireNonNull(objectService, "objectService");
  }

  @Override
  public void initialize() {
    if (isInitialized()) {
      return;
    }

    OrderDatabaseWorker newWorker = new OrderDatabaseWorker(
        settings.queueCapacity(),
        new OrderDatabaseWriter(connectionFactory(settings), new OrderRowMapper())
    );
    newWorker.start();
    worker = newWorker;
    eventSource.subscribe(this);

    // Record the orders that already exist, e.g. when the extension starts with a running plant.
    Instant now = Instant.now();
    for (TransportOrder order : objectService.fetchObjects(TransportOrder.class)) {
      newWorker.offer(new OrderChange(now, OrderChange.Type.SNAPSHOT, null, order));
    }

    LOG.info("Order database: recording transport order changes to {}", settings.jdbcUrl());
    initialized = true;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void terminate() {
    if (!isInitialized()) {
      return;
    }

    eventSource.unsubscribe(this);
    OrderDatabaseWorker oldWorker = worker;
    worker = null;
    oldWorker.stop(FLUSH_TIMEOUT);
    LOG.info(
        "Order database: stopped. {} changes were dropped because the queue was full.",
        oldWorker.getDroppedChanges()
    );
    initialized = false;
  }

  @Override
  public void onEvent(Object event) {
    OrderDatabaseWorker currentWorker = worker;
    if (currentWorker == null) {
      return;
    }
    OrderChange change = toOrderChange(event, Instant.now());
    if (change != null) {
      currentWorker.offer(change);
    }
  }

  /**
   * Turns a kernel event into an order change.
   *
   * @param event The event.
   * @param time When the event happened.
   * @return The change, or {@code null} if the event isn't about a transport order.
   */
  static OrderChange toOrderChange(Object event, Instant time) {
    if (!(event instanceof TCSObjectEvent objectEvent)) {
      return null;
    }
    if (!(objectEvent.getCurrentOrPreviousObjectState() instanceof TransportOrder order)) {
      return null;
    }

    TransportOrder previous = objectEvent.getPreviousObjectState() instanceof TransportOrder p
        ? p
        : null;
    OrderChange.Type type = switch (objectEvent.getType()) {
      case OBJECT_CREATED -> OrderChange.Type.CREATED;
      case OBJECT_MODIFIED -> OrderChange.Type.MODIFIED;
      case OBJECT_REMOVED -> OrderChange.Type.REMOVED;
    };
    return new OrderChange(time, type, previous, order);
  }

  private static ConnectionFactory connectionFactory(OrderDatabaseSettings settings) {
    Properties properties = new Properties();
    properties.setProperty("user", settings.username());
    properties.setProperty("password", settings.password());
    // PostgreSQL driver options: don't wait forever for an unreachable or hanging database.
    properties.setProperty("connectTimeout", "10");
    properties.setProperty("socketTimeout", "30");
    properties.setProperty("ApplicationName", "openTCS kernel");
    return () -> DriverManager.getConnection(settings.jdbcUrl(), properties);
  }
}
