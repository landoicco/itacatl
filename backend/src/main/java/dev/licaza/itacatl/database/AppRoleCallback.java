package dev.licaza.itacatl.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
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
      // The datasource's, so the role always has the password the application connects with
      @Value("${spring.datasource.password:}") String password) {
    this.iamAuthentication = iamAuthentication;
    this.password = password;
  }

  @Override
  public boolean supports(Event event, Context context) {
    return event == Event.BEFORE_MIGRATE;
  }

  @Override
  public boolean canHandleInTransaction(Event event, Context context) {
    return true;
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

    Boolean bypassesPolicies = bypassesPolicies(connection, role);
    if (bypassesPolicies == null) {
      // Attributes are only set on creation: changing SUPERUSER or BYPASSRLS later needs a
      // superuser, which the RDS master user is not
      execute(
          connection, "CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS", role);
      log.info("✅ Created the database role {}", role);
    } else if (bypassesPolicies) {
      throw new IllegalStateException(
          "Role " + role + " is a superuser or has BYPASSRLS: Row-Level Security would not apply");
    }

    if (iamAuthentication) {
      // rds_iam exists only in RDS. A role in it can only sign in with an IAM token
      execute(connection, "GRANT rds_iam TO %I", role);
      execute(connection, "ALTER ROLE %I PASSWORD NULL", role);
    } else {
      execute(connection, "ALTER ROLE %I PASSWORD %L", role, password);
    }
    log.info(
        "✅ Database role {} ready, signing in with {}",
        role,
        iamAuthentication ? "IAM" : "a password");
  }

  // null when the role does not exist
  private static Boolean bypassesPolicies(Connection connection, String role) throws SQLException {
    try (PreparedStatement query =
        connection.prepareStatement(
            "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = ?")) {
      query.setString(1, role);
      try (ResultSet result = query.executeQuery()) {
        return result.next() ? result.getBoolean(1) : null;
      }
    }
  }

  /**
   * Runs a statement that cannot take bind parameters, as role statements cannot. PostgreSQL's
   * format() quotes each argument (%I as an identifier, %L as a literal), so a password with quotes
   * or backslashes is never parsed as SQL.
   */
  private static void execute(Connection connection, String template, String... arguments)
      throws SQLException {
    String sql;
    try (PreparedStatement format =
        connection.prepareStatement("SELECT format(?" + ", ?".repeat(arguments.length) + ")")) {
      format.setString(1, template);
      for (int i = 0; i < arguments.length; i++) {
        format.setString(i + 2, arguments[i]);
      }
      try (ResultSet result = format.executeQuery()) {
        result.next();
        sql = result.getString(1);
      }
    }
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
