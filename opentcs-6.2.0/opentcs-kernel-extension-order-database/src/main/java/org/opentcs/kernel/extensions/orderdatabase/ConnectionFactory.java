package org.opentcs.kernel.extensions.orderdatabase;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Opens connections to the order database.
 */
@FunctionalInterface
public interface ConnectionFactory {

  /**
   * Opens a new connection.
   *
   * @return The connection.
   * @throws SQLException If the database can't be reached or refuses the login.
   */
  Connection open()
      throws SQLException;
}
