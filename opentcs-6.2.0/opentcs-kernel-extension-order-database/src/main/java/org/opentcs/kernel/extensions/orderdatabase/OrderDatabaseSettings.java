package org.opentcs.kernel.extensions.orderdatabase;

import static java.util.Objects.requireNonNull;

/**
 * The order database settings, read once when the kernel starts.
 *
 * @param jdbcUrl The JDBC URL of the database.
 * @param username The database login.
 * @param password The password of the database login.
 * @param queueCapacity The maximum number of order changes waiting to be written.
 */
public record OrderDatabaseSettings(
    String jdbcUrl,
    String username,
    String password,
    int queueCapacity
) {

  /**
   * Creates a new instance.
   *
   * @param jdbcUrl The JDBC URL of the database.
   * @param username The database login.
   * @param password The password of the database login.
   * @param queueCapacity The maximum number of order changes waiting to be written.
   */
  public OrderDatabaseSettings {
    requireNonNull(jdbcUrl, "jdbcUrl");
    requireNonNull(username, "username");
    requireNonNull(password, "password");
    if (jdbcUrl.isBlank()) {
      throw new IllegalArgumentException("jdbcUrl is empty");
    }
    if (queueCapacity < 1) {
      throw new IllegalArgumentException("queueCapacity must be at least 1: " + queueCapacity);
    }
  }

  @Override
  public String toString() {
    // Leaves out the password, so it never ends up in a log.
    return "OrderDatabaseSettings{jdbcUrl=" + jdbcUrl
        + ", username=" + username
        + ", queueCapacity=" + queueCapacity + "}";
  }
}
