package org.opentcs.kernel.extensions.orderdatabase;

import org.opentcs.configuration.ConfigurationEntry;
import org.opentcs.configuration.ConfigurationPrefix;

/**
 * Configuration entries for the order database extension.
 */
@ConfigurationPrefix(OrderDatabaseConfiguration.PREFIX)
public interface OrderDatabaseConfiguration {

  /**
   * The prefix for all configuration entries here.
   */
  String PREFIX = "orderdatabase";

  @ConfigurationEntry(
      type = "Boolean",
      description = "Whether to write every transport order change to the database.",
      changesApplied = ConfigurationEntry.ChangesApplied.ON_APPLICATION_START,
      orderKey = "0"
  )
  boolean enable();

  @ConfigurationEntry(
      type = "String",
      description = "JDBC URL of the PostgreSQL database, "
          + "e.g. jdbc:postgresql://localhost:5432/fms_orders.",
      changesApplied = ConfigurationEntry.ChangesApplied.ON_APPLICATION_START,
      orderKey = "1"
  )
  String jdbcUrl();

  @ConfigurationEntry(
      type = "String",
      description = "Database login.",
      changesApplied = ConfigurationEntry.ChangesApplied.ON_APPLICATION_START,
      orderKey = "2"
  )
  String username();

  @ConfigurationEntry(
      type = "String",
      description = "Password of the database login.",
      changesApplied = ConfigurationEntry.ChangesApplied.ON_APPLICATION_START,
      orderKey = "3"
  )
  String password();

  @ConfigurationEntry(
      type = "Integer",
      description = "Maximum number of order changes waiting to be written while the database "
          + "is slow or unreachable. Further changes are dropped.",
      changesApplied = ConfigurationEntry.ChangesApplied.ON_APPLICATION_START,
      orderKey = "4"
  )
  int queueCapacity();
}
