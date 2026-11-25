package org.opentcs.kernel.extensions.orderdatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentcs.data.order.TransportOrder;

/**
 * Tests for {@link OrderDatabaseWriter}, with a mocked database connection.
 */
class OrderDatabaseWriterTest {

  private Connection connection;
  private Statement statement;
  private PreparedStatement upsert;
  private PreparedStatement insert;
  private OrderDatabaseWriter writer;

  @BeforeEach
  void setUp()
      throws SQLException {
    connection = mock(Connection.class);
    statement = mock(Statement.class);
    upsert = mock(PreparedStatement.class);
    insert = mock(PreparedStatement.class);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.prepareStatement(startsWith("INSERT INTO transport_orders ")))
        .thenReturn(upsert);
    when(connection.prepareStatement(startsWith("INSERT INTO transport_order_changes ")))
        .thenReturn(insert);
    writer = new OrderDatabaseWriter(() -> connection, new OrderRowMapper());
  }

  @Test
  void createsTablesAndWritesOrderAndChangeInOneTransaction()
      throws SQLException {
    writer.write(change(OrderChange.Type.CREATED, null, TestOrders.order("TOrder-1")));

    verify(statement).execute(startsWith("CREATE TABLE IF NOT EXISTS transport_orders "));
    verify(statement).execute(startsWith("CREATE TABLE IF NOT EXISTS transport_order_changes "));
    verify(upsert).setString(1, "TOrder-1");
    verify(upsert).executeUpdate();
    verify(insert).setString(3, "CREATED");
    verify(insert).setString(8, "created");
    verify(insert).executeUpdate();
    verify(connection).commit();
  }

  @Test
  void updatesOnlyTheOrderRowForRouteStepProgress()
      throws SQLException {
    TransportOrder before = TestOrders.order("TOrder-2").withCurrentRouteStepIndex(0);

    writer.write(change(OrderChange.Type.MODIFIED, before, before.withCurrentRouteStepIndex(1)));

    verify(upsert).executeUpdate();
    verify(connection, never()).prepareStatement(
        startsWith("INSERT INTO transport_order_changes ")
    );
    verify(connection).commit();
  }

  @Test
  void rollsBackAndRethrowsWhenWritingFails()
      throws SQLException {
    when(upsert.executeUpdate()).thenThrow(new SQLException("value too long", "22001"));

    assertThatThrownBy(
        () -> writer.write(change(OrderChange.Type.CREATED, null, TestOrders.order("TOrder-3")))
    ).isInstanceOf(SQLException.class);
    verify(connection).rollback();
    verify(connection, never()).commit();
  }

  @Test
  void connectsOnlyOnce()
      throws SQLException {
    writer.write(change(OrderChange.Type.CREATED, null, TestOrders.order("TOrder-4")));
    writer.write(change(OrderChange.Type.CREATED, null, TestOrders.order("TOrder-5")));

    verify(connection, atLeastOnce()).createStatement();
    verify(statement).execute(startsWith("CREATE TABLE IF NOT EXISTS transport_orders "));
  }

  @Test
  void recognizesConnectionProblems() {
    assertThat(OrderDatabaseWriter.isConnectionProblem(new SQLException("refused", "08001")))
        .isTrue();
    assertThat(OrderDatabaseWriter.isConnectionProblem(new SQLRecoverableException("gone")))
        .isTrue();
    assertThat(OrderDatabaseWriter.isConnectionProblem(new SQLException("too long", "22001")))
        .isFalse();
  }

  private OrderChange change(OrderChange.Type type, TransportOrder previous, TransportOrder order) {
    return new OrderChange(Instant.parse("2026-11-25T10:00:00Z"), type, previous, order);
  }
}
