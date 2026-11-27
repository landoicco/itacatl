package dev.licaza.itacatl.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates itacatl_app, the role the application connects as, before Flyway migrates: the migrations
 * grant it privileges by name, so it must exist first. It runs as Flyway's user, the tables' owner,
 * on every start, so the role follows its configured sign-in when that changes. The same code runs
 * in docker compose, the tests and RDS. See docs/tenant-isolation.md.
 */
@Component
public class AppRoleCallback implements Callback {

  static final String ROLE = "itacatl_app";

  private static final Logger log = LoggerFactory.getLogger(AppRoleCallback.class);

  private final boolean iamAuthentication;
  private final String password;

  public AppRoleCallback(
      // RDS only: the application signs in with a token from its IAM role instead of a password
      @Value("${itacatl.app-role.iam-authentication:false}") boolean iamAuthentication,
      // Read directly, not through spring.datasource.password, so with IAM it may be unset
      @Value("${DB_APP_PASSWORD:}") String password) {
    this.iamAuthentication = iamAuthentication;
    this.password = password;
  }

  @Override
  public boolean supports(Event event, Context context) {
    return event == Event.BEFORE_MIGRATE;
  }

  // Each statement commits at once, so an instance waiting on the lock sees the role once it has it
  @Override
  public boolean canHandleInTransaction(Event event, Context context) {
    return false;
  }

  @Override
  public void handle(Event event, Context context) {
    try {
      ensureRole(context.getConnection(), ROLE);
    } catch (SQLException e) {
      throw new IllegalStateException("❌ Could not prepare the database role " + ROLE, e);
    }
  }

  @Override
  public String getCallbackName() {
    return "appRole";
  }

  void ensureRole(Connection connection, String role) throws SQLException {
    // Checked first, so a misconfigured start leaves the database as it found it
    if (!iamAuthentication && (password == null || password.isBlank())) {
      throw new IllegalStateException(
          "Role " + role + " needs a password: set DB_APP_PASSWORD, or IAM authentication");
    }

    // Two instances starting at once, as during an instance refresh, would race on CREATE ROLE;
    // the second waits here until the first is done
    query(connection, "SELECT pg_advisory_lock(hashtext(?))", role);
    try {
      prepare(connection, role);
    } finally {
      query(connection, "SELECT pg_advisory_unlock(hashtext(?))", role);
    }
  }

  private void prepare(Connection connection, String role) throws SQLException {
    PGConnection postgres = connection.unwrap(PGConnection.class);
    String quotedRole = postgres.escapeIdentifier(role);

    Optional<List<String>> excess = excessPrivileges(connection, role);
    if (excess.isEmpty()) {
      // Attributes are only set on creation: changing SUPERUSER or BYPASSRLS later needs a
      // superuser, which the RDS master user is not
      execute(
          connection,
          "CREATE ROLE " + quotedRole + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS");
      log.info("✅ Created the database role {}", role);
    } else if (!excess.get().isEmpty()) {
      throw new IllegalStateException(
          "Role " + role + " must only sign in and use data, but it has " + excess.get());
    }

    boolean signsInWithIam = isMember(connection, role, "rds_iam");
    if (iamAuthentication) {
      // rds_iam exists only in RDS. A role in it can only sign in with an IAM token
      if (!signsInWithIam) {
        execute(connection, "GRANT rds_iam TO " + quotedRole);
      }
      execute(connection, "ALTER ROLE " + quotedRole + " PASSWORD NULL");
    } else {
      // RDS would keep refusing passwords from a role that stays in rds_iam
      if (signsInWithIam) {
        execute(connection, "REVOKE rds_iam FROM " + quotedRole);
      }
      // Hashed here (SCRAM), so the password never reaches the server, or its logs, in plain text
      postgres.alterUserPassword(role, password.toCharArray(), "scram-sha-256");
    }
    log.info(
        "✅ Database role {} ready, signing in with {}",
        role,
        iamAuthentication ? "IAM" : "a password");
  }

  /**
   * What the role may do beyond signing in and using data: each item would let it escape the
   * policies (SUPERUSER, BYPASSRLS, owning a table) or gain more than it needs. Empty when the role
   * does not exist.
   */
  private static Optional<List<String>> excessPrivileges(Connection connection, String role)
      throws SQLException {
    try (PreparedStatement query =
        connection.prepareStatement(
            """
            SELECT rolsuper, rolbypassrls, rolcreaterole, rolcreatedb,
                   EXISTS (SELECT FROM pg_class WHERE relowner = r.oid)
            FROM pg_roles r
            WHERE rolname = ?
            """)) {
      query.setString(1, role);
      try (ResultSet result = query.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        List<String> excess = new ArrayList<>();
        String[] names = {"SUPERUSER", "BYPASSRLS", "CREATEROLE", "CREATEDB", "tables it owns"};
        for (int i = 0; i < names.length; i++) {
          if (result.getBoolean(i + 1)) {
            excess.add(names[i]);
          }
        }
        return Optional.of(excess);
      }
    }
  }

  // False also when the group does not exist, as rds_iam outside RDS
  private static boolean isMember(Connection connection, String role, String group)
      throws SQLException {
    try (PreparedStatement query =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT FROM pg_auth_members m
                JOIN pg_roles g ON g.oid = m.roleid
                JOIN pg_roles u ON u.oid = m.member
                WHERE g.rolname = ? AND u.rolname = ?)
            """)) {
      query.setString(1, group);
      query.setString(2, role);
      try (ResultSet result = query.executeQuery()) {
        result.next();
        return result.getBoolean(1);
      }
    }
  }

  private static void query(Connection connection, String sql, String argument)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, argument);
      statement.executeQuery().close();
    }
  }

  // Role statements take no bind parameters: the role is quoted by escapeIdentifier
  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
