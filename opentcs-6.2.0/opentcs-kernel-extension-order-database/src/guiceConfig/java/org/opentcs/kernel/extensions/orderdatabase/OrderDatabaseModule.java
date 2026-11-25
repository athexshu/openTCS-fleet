package org.opentcs.kernel.extensions.orderdatabase;

import jakarta.inject.Singleton;
import org.opentcs.customizations.kernel.KernelInjectionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the order database extension.
 * <p>
 * If the configuration is missing or invalid, the extension stays off and the kernel starts as
 * usual.
 * </p>
 */
public class OrderDatabaseModule
    extends
      KernelInjectionModule {

  /**
   * This class's logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(OrderDatabaseModule.class);

  /**
   * Creates a new instance.
   */
  public OrderDatabaseModule() {
  }

  @Override
  protected void configure() {
    OrderDatabaseSettings settings = readSettings();
    if (settings == null) {
      return;
    }

    bind(OrderDatabaseSettings.class)
        .toInstance(settings);

    extensionsBinderOperating().addBinding()
        .to(OrderDatabaseExtension.class)
        .in(Singleton.class);
  }

  private OrderDatabaseSettings readSettings() {
    try {
      OrderDatabaseConfiguration configuration = getConfigBindingProvider().get(
          OrderDatabaseConfiguration.PREFIX,
          OrderDatabaseConfiguration.class
      );
      if (!configuration.enable()) {
        LOG.info("Order database disabled by configuration.");
        return null;
      }

      OrderDatabaseSettings settings = new OrderDatabaseSettings(
          configuration.jdbcUrl(),
          configuration.username(),
          configuration.password(),
          configuration.queueCapacity()
      );
      LOG.info("Order database enabled: {}", settings);
      return settings;
    }
    catch (RuntimeException e) {
      LOG.warn(
          "Order database disabled: its configuration ({}.*) is missing or invalid: {}",
          OrderDatabaseConfiguration.PREFIX,
          e.getMessage()
      );
      return null;
    }
  }
}
