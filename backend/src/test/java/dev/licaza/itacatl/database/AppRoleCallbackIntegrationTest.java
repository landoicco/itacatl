package dev.licaza.itacatl.database;

import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the callback as the owner against roles of its own, so the itacatl_app the other tests
 * connect as is never changed. Roles belong to the whole server, so each test names its own.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// Role statements commit at once instead of rolling back with a test transaction
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppRoleCallbackIntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private PostgreSQLContainer postgres;

  @Test
  void itacatlApp_ExistsBeforeTheMigrations() throws SQLException {
    // The migrations granted it privileges, so the context would not have started otherwise
    assertTrue(canSignIn(AppRoleCallback.ROLE, "itacatl-app-test"));
  }

  @Test
  void ensureRole_CreatesARoleThatCannotBypassRowLevelSecurity() throws SQLException {
    ensureRole("callback_created", new AppRoleCallback(false, "first-password"));

    assertTrue(
        query(
            "SELECT rolcanlogin AND NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls)"
                + " FROM pg_roles WHERE rolname = 'callback_created'"));
    assertTrue(canSignIn("callback_created", "first-password"));
  }

  @Test
  void ensureRole_GivesAnExistingRoleTheCurrentPassword() throws SQLException {
    ensureRole("callback_rotated", new AppRoleCallback(false, "old-password"));

    ensureRole("callback_rotated", new AppRoleCallback(false, "new-password"));

    assertTrue(canSignIn("callback_rotated", "new-password"));
    assertFalse(canSignIn("callback_rotated", "old-password"));
  }

  @Test
  void ensureRole_QuotesThePassword() throws SQLException {
    String password = "it's a \\ test'; DROP ROLE callback_quoted; --";

    ensureRole("callback_quoted", new AppRoleCallback(false, password));

    assertTrue(canSignIn("callback_quoted", password));
  }

  @Test
  void ensureRole_RefusesARoleThatBypassesRowLevelSecurity() throws SQLException {
    run("CREATE ROLE callback_bypass LOGIN BYPASSRLS");
    AppRoleCallback callback = new AppRoleCallback(false, "password");

    assertThrows(IllegalStateException.class, () -> ensureRole("callback_bypass", callback));
  }

  @Test
  void ensureRole_RefusesAPasswordThatIsMissingBeforeCreatingTheRole() throws SQLException {
    AppRoleCallback callback = new AppRoleCallback(false, "");

    assertThrows(IllegalStateException.class, () -> ensureRole("callback_no_password", callback));
    assertFalse(
        query("SELECT EXISTS (SELECT FROM pg_roles WHERE rolname = 'callback_no_password')"));
  }

  @Test
  void ensureRole_WithIamGrantsRdsIamAndRemovesThePassword() throws SQLException {
    // rds_iam exists only in RDS; a role of the same name stands in for it
    run("DO $$ BEGIN CREATE ROLE rds_iam; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
    ensureRole("callback_iam", new AppRoleCallback(false, "password"));

    ensureRole("callback_iam", new AppRoleCallback(true, null));

    assertTrue(query("SELECT pg_has_role('callback_iam', 'rds_iam', 'MEMBER')"));
    assertFalse(canSignIn("callback_iam", "password"));
  }

  private void ensureRole(String role, AppRoleCallback callback) throws SQLException {
    try (Connection owner = dataSource.getConnection()) {
      callback.ensureRole(owner, role);
    }
  }

  private boolean canSignIn(String role, String password) {
    try (Connection ignored = DriverManager.getConnection(postgres.getJdbcUrl(), role, password)) {
      return true;
    } catch (SQLException e) {
      return false;
    }
  }

  private void run(String sql) throws SQLException {
    try (Connection owner = dataSource.getConnection();
        Statement statement = owner.createStatement()) {
      statement.execute(sql);
    }
  }

  private boolean query(String sql) throws SQLException {
    try (Connection owner = dataSource.getConnection();
        Statement statement = owner.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getBoolean(1);
    }
  }
}
