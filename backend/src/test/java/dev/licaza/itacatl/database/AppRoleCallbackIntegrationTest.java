package dev.licaza.itacatl.database;

import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the callback as the owner against roles of its own, so the itacatl_app the other tests
 * connect as is never changed. Roles belong to the whole server, so each test names its own and
 * they are dropped after it.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// Role statements commit at once instead of rolling back with a test transaction
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppRoleCallbackIntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private PostgreSQLContainer postgres;

  @Value("${DB_APP_PASSWORD}")
  private String appPassword;

  @AfterEach
  void dropTestRoles() throws SQLException {
    run("DROP TABLE IF EXISTS callback_owned");
    run(
        """
        DO $$
        DECLARE r record;
        BEGIN
            FOR r IN SELECT rolname FROM pg_roles WHERE rolname LIKE 'callback\\_%' LOOP
                EXECUTE format('DROP ROLE %I', r.rolname);
            END LOOP;
        END
        $$
        """);
  }

  @Test
  void itacatlApp_ExistsBeforeTheMigrations() {
    // The migrations granted it privileges, so the context would not have started otherwise
    assertTrue(canSignIn(AppRoleCallback.ROLE, appPassword));
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
  void ensureRole_SendsThePasswordHashed() throws SQLException {
    ensureRole("callback_hashed", new AppRoleCallback(false, "a-password"));

    assertTrue(
        query(
            "SELECT rolpassword LIKE 'SCRAM-SHA-256$%' FROM pg_authid"
                + " WHERE rolname = 'callback_hashed'"));
  }

  @Test
  void ensureRole_GivesAnExistingRoleTheCurrentPassword() throws SQLException {
    ensureRole("callback_rotated", new AppRoleCallback(false, "old-password"));

    ensureRole("callback_rotated", new AppRoleCallback(false, "new-password"));

    assertTrue(canSignIn("callback_rotated", "new-password"));
    assertFalse(canSignIn("callback_rotated", "old-password"));
  }

  @Test
  void ensureRole_AcceptsAnyPassword() throws SQLException {
    String password = "it's a \\ test'; DROP ROLE callback_quoted; --";

    ensureRole("callback_quoted", new AppRoleCallback(false, password));

    assertTrue(canSignIn("callback_quoted", password));
  }

  @Test
  void ensureRole_WaitsForAnotherInstanceStartingAtTheSameTime() throws Exception {
    AppRoleCallback callback = new AppRoleCallback(false, "a-password");
    Callable<Void> start =
        () -> {
          ensureRole("callback_concurrent", callback);
          return null;
        };

    try (ExecutorService instances = Executors.newFixedThreadPool(2)) {
      Future<Void> first = instances.submit(start);
      Future<Void> second = instances.submit(start);

      first.get();
      second.get();
    }
    assertTrue(canSignIn("callback_concurrent", "a-password"));
  }

  @Test
  void ensureRole_RefusesARoleThatBypassesRowLevelSecurity() throws SQLException {
    run("CREATE ROLE callback_bypass LOGIN BYPASSRLS");
    AppRoleCallback callback = new AppRoleCallback(false, "a-password");

    assertThrows(IllegalStateException.class, () -> ensureRole("callback_bypass", callback));
  }

  @Test
  void ensureRole_RefusesARoleThatCanCreateRoles() throws SQLException {
    run("CREATE ROLE callback_creator LOGIN CREATEROLE");
    AppRoleCallback callback = new AppRoleCallback(false, "a-password");

    assertThrows(IllegalStateException.class, () -> ensureRole("callback_creator", callback));
  }

  @Test
  void ensureRole_RefusesARoleThatOwnsATable() throws SQLException {
    // Policies do not apply to a table's owner
    run("CREATE ROLE callback_owner LOGIN");
    run("CREATE TABLE callback_owned (id INT)");
    run("ALTER TABLE callback_owned OWNER TO callback_owner");
    AppRoleCallback callback = new AppRoleCallback(false, "a-password");

    assertThrows(IllegalStateException.class, () -> ensureRole("callback_owner", callback));
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
    createRdsIam();
    ensureRole("callback_iam", new AppRoleCallback(false, "a-password"));

    ensureRole("callback_iam", new AppRoleCallback(true, ""));

    assertTrue(query("SELECT pg_has_role('callback_iam', 'rds_iam', 'MEMBER')"));
    assertFalse(canSignIn("callback_iam", "a-password"));
  }

  @Test
  void ensureRole_BackToAPasswordLeavesRdsIam() throws SQLException {
    createRdsIam();
    ensureRole("callback_back", new AppRoleCallback(true, ""));

    ensureRole("callback_back", new AppRoleCallback(false, "a-password"));

    assertFalse(query("SELECT pg_has_role('callback_back', 'rds_iam', 'MEMBER')"));
    assertTrue(canSignIn("callback_back", "a-password"));
  }

  // rds_iam exists only in RDS; a role of the same name stands in for it. It is left in place:
  // other tests may use it, and nothing outside these tests looks for it
  private void createRdsIam() throws SQLException {
    run("DO $$ BEGIN CREATE ROLE rds_iam; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
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
