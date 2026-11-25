package org.opentcs.kernel.extensions.orderdatabase;

import static java.util.Objects.requireNonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes order changes to the database with plain JDBC.
 * An instance must only be used by one thread.
 */
public class OrderDatabaseWriter
    implements
      AutoCloseable {

  /**
   * This class's logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(OrderDatabaseWriter.class);
  /**
   * Creates the tables and indexes if they don't exist yet.
   */
  private static final List<String> SCHEMA = List.of(
      "CREATE TABLE IF NOT EXISTS transport_orders ("
          + "name text PRIMARY KEY, "
          + "type text, "
          + "state text NOT NULL, "
          + "intended_vehicle text, "
          + "processing_vehicle text, "
          + "creation_time timestamptz, "
          + "finished_time timestamptz, "
          + "deadline timestamptz, "
          + "dispensable boolean NOT NULL, "
          + "wrapping_sequence text, "
          + "peripheral_reservation_token text, "
          + "current_drive_order_index integer NOT NULL, "
          + "current_route_step_index integer NOT NULL, "
          + "properties jsonb NOT NULL, "
          + "dependencies jsonb NOT NULL, "
          + "drive_orders jsonb NOT NULL, "
          + "history jsonb NOT NULL, "
          + "last_change_time timestamptz NOT NULL, "
          + "removed_from_kernel_time timestamptz, "
          + "final_state_time timestamptz)",
      // Added after the first version of the table.
      "ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS final_state_time timestamptz",
      "CREATE INDEX IF NOT EXISTS transport_orders_state_idx ON transport_orders (state)",
      "CREATE INDEX IF NOT EXISTS transport_orders_processing_vehicle_idx "
          + "ON transport_orders (processing_vehicle)",
      "CREATE INDEX IF NOT EXISTS transport_orders_creation_time_idx "
          + "ON transport_orders (creation_time)",
      "CREATE TABLE IF NOT EXISTS transport_order_changes ("
          + "id bigserial PRIMARY KEY, "
          + "order_name text NOT NULL, "
          + "change_time timestamptz NOT NULL, "
          + "change_type text NOT NULL, "
          + "state text NOT NULL, "
          + "processing_vehicle text, "
          + "current_drive_order_index integer NOT NULL, "
          + "drive_order_states jsonb NOT NULL, "
          + "changes text NOT NULL)",
      "CREATE INDEX IF NOT EXISTS transport_order_changes_order_idx "
          + "ON transport_order_changes (order_name, change_time)"
  );
  /**
   * Inserts an order or updates its row.
   */
  private static final String UPSERT_ORDER = "INSERT INTO transport_orders ("
      + "name, type, state, intended_vehicle, processing_vehicle, "
      + "creation_time, finished_time, deadline, dispensable, wrapping_sequence, "
      + "peripheral_reservation_token, current_drive_order_index, current_route_step_index, "
      + "properties, dependencies, drive_orders, history, "
      + "last_change_time, removed_from_kernel_time, final_state_time) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
      + "?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?) "
      + "ON CONFLICT (name) DO UPDATE SET "
      + "type = EXCLUDED.type, "
      + "state = EXCLUDED.state, "
      + "intended_vehicle = EXCLUDED.intended_vehicle, "
      + "processing_vehicle = EXCLUDED.processing_vehicle, "
      + "creation_time = EXCLUDED.creation_time, "
      + "finished_time = EXCLUDED.finished_time, "
      + "deadline = EXCLUDED.deadline, "
      + "dispensable = EXCLUDED.dispensable, "
      + "wrapping_sequence = EXCLUDED.wrapping_sequence, "
      + "peripheral_reservation_token = EXCLUDED.peripheral_reservation_token, "
      + "current_drive_order_index = EXCLUDED.current_drive_order_index, "
      + "current_route_step_index = EXCLUDED.current_route_step_index, "
      + "properties = EXCLUDED.properties, "
      + "dependencies = EXCLUDED.dependencies, "
      + "drive_orders = EXCLUDED.drive_orders, "
      + "history = EXCLUDED.history, "
      + "last_change_time = EXCLUDED.last_change_time, "
      + "removed_from_kernel_time = EXCLUDED.removed_from_kernel_time, "
      + "final_state_time = EXCLUDED.final_state_time";
  /**
   * Adds a row to the change history.
   */
  private static final String INSERT_CHANGE = "INSERT INTO transport_order_changes ("
      + "order_name, change_time, change_type, state, processing_vehicle, "
      + "current_drive_order_index, drive_order_states, changes) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)";
  /**
   * Opens the database connections.
   */
  private final ConnectionFactory connectionFactory;
  /**
   * Maps orders to column values.
   */
  private final OrderRowMapper mapper;
  /**
   * The current connection, or {@code null} if not connected.
   */
  private Connection connection;

  /**
   * Creates a new instance.
   *
   * @param connectionFactory Opens the database connections.
   * @param mapper Maps orders to column values.
   */
  public OrderDatabaseWriter(ConnectionFactory connectionFactory, OrderRowMapper mapper) {
    this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory");
    this.mapper = requireNonNull(mapper, "mapper");
  }

  /**
   * Opens the connection and creates the tables if needed. Does nothing if already connected.
   *
   * @throws SQLException If the database can't be reached or the tables can't be created.
   */
  public void connect()
      throws SQLException {
    if (connection != null) {
      return;
    }
    Connection newConnection = connectionFactory.open();
    try {
      newConnection.setAutoCommit(true);
      try (Statement statement = newConnection.createStatement()) {
        for (String sql : SCHEMA) {
          statement.execute(sql);
        }
      }
      newConnection.setAutoCommit(false);
    }
    catch (SQLException e) {
      closeQuietly(newConnection);
      throw e;
    }
    connection = newConnection;
  }

  /**
   * Writes one change in one transaction: the order's current values and, if something worth
   * recording changed, a row in the change history.
   *
   * @param change The change.
   * @throws SQLException If writing failed. The transaction is rolled back.
   */
  public void write(OrderChange change)
      throws SQLException {
    requireNonNull(change, "change");
    connect();

    OrderRow row = mapper.toRow(change.order());
    String changes = describe(change);
    try {
      upsertOrder(row, change);
      if (!changes.isEmpty()) {
        insertChange(row, change, changes);
      }
      connection.commit();
    }
    catch (SQLException e) {
      rollbackQuietly();
      throw e;
    }
  }

  /**
   * Checks whether an exception means that the connection to the database is broken, as
   * opposed to a problem with the data written.
   *
   * @param e The exception.
   * @return Whether the connection is broken.
   */
  public static boolean isConnectionProblem(SQLException e) {
    String sqlState = e.getSQLState();
    return (sqlState != null && sqlState.startsWith("08"))
        || e instanceof SQLTransientConnectionException
        || e instanceof SQLNonTransientConnectionException
        || e instanceof SQLRecoverableException;
  }

  @Override
  public void close() {
    closeQuietly(connection);
    connection = null;
  }

  private String describe(OrderChange change) {
    return switch (change.type()) {
      case SNAPSHOT -> "already in the kernel when recording started";
      case CREATED -> "created";
      case REMOVED -> "removed from the kernel";
      case MODIFIED -> change.previous() == null
          ? "modified"
          : mapper.describeChanges(change.previous(), change.order());
    };
  }

  private void upsertOrder(OrderRow row, OrderChange change)
      throws SQLException {
    OffsetDateTime changeTime = change.time().atOffset(ZoneOffset.UTC);
    try (PreparedStatement statement = connection.prepareStatement(UPSERT_ORDER)) {
      statement.setString(1, row.name());
      statement.setString(2, row.type());
      statement.setString(3, row.state());
      statement.setString(4, row.intendedVehicle());
      statement.setString(5, row.processingVehicle());
      setTimestamp(statement, 6, row.creationTime());
      setTimestamp(statement, 7, row.finishedTime());
      setTimestamp(statement, 8, row.deadline());
      statement.setBoolean(9, row.dispensable());
      statement.setString(10, row.wrappingSequence());
      statement.setString(11, row.peripheralReservationToken());
      statement.setInt(12, row.currentDriveOrderIndex());
      statement.setInt(13, row.currentRouteStepIndex());
      statement.setString(14, row.propertiesJson());
      statement.setString(15, row.dependenciesJson());
      statement.setString(16, row.driveOrdersJson());
      statement.setString(17, row.historyJson());
      setTimestamp(statement, 18, changeTime);
      setTimestamp(
          statement,
          19,
          change.type() == OrderChange.Type.REMOVED ? changeTime : null
      );
      setTimestamp(statement, 20, row.finalStateTime());
      statement.executeUpdate();
    }
  }

  private void insertChange(OrderRow row, OrderChange change, String changes)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_CHANGE)) {
      statement.setString(1, row.name());
      setTimestamp(statement, 2, change.time().atOffset(ZoneOffset.UTC));
      statement.setString(3, change.type().name());
      statement.setString(4, row.state());
      statement.setString(5, row.processingVehicle());
      statement.setInt(6, row.currentDriveOrderIndex());
      statement.setString(7, row.driveOrderStatesJson());
      statement.setString(8, changes);
      statement.executeUpdate();
    }
  }

  private static void setTimestamp(PreparedStatement statement, int index, OffsetDateTime time)
      throws SQLException {
    if (time == null) {
      statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
    }
    else {
      statement.setObject(index, time);
    }
  }

  private void rollbackQuietly() {
    if (connection == null) {
      return;
    }
    try {
      connection.rollback();
    }
    catch (SQLException e) {
      LOG.debug("Rollback failed", e);
    }
  }

  private static void closeQuietly(Connection connection) {
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    }
    catch (SQLException e) {
      LOG.debug("Closing the database connection failed", e);
    }
  }
}
