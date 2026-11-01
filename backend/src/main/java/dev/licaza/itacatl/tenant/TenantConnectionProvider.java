package dev.licaza.itacatl.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Hands Hibernate its connections with the session's tenant set in PostgreSQL, where the Row-Level
 * Security policies read it ({@code app_tenant()} in V4). The value is the one Hibernate passes for
 * the session, the same one its tenant filter uses, so the two layers always agree. See
 * docs/tenant-isolation.md.
 *
 * <p>A Spring bean, unlike {@link TenantIdentifierResolver}, because it needs the pool. Test slices
 * such as {@code @DataJpaTest} do not load it, and connect as the tables' owner, whom the policies
 * do not apply to.
 */
@Component
public class TenantConnectionProvider
    implements MultiTenantConnectionProvider<Long>, HibernatePropertiesCustomizer {

  private static final Logger log = LoggerFactory.getLogger(TenantConnectionProvider.class);

  private final DataSource dataSource;

  public TenantConnectionProvider(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
  }

  // For work outside a session, such as reading the schema's metadata. Nothing is set, so the
  // policies see no business
  @Override
  public Connection getAnyConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(Long tenantId) throws SQLException {
    Connection connection = dataSource.getConnection();
    // For the connection's whole session, not one transaction: Hibernate takes the connection
    // before it begins the transaction. Always set, so whatever a previous borrower left is
    // replaced
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
      statement.setString(1, tenantId.toString());
      statement.execute();
    } catch (SQLException ex) {
      connection.close();
      throw ex;
    }
    return connection;
  }

  // The pool keeps the connection open, so the setting is cleared before it is reused, also by
  // code that takes connections from the pool directly (Spring Session, Flyway)
  @Override
  public void releaseConnection(Long tenantId, Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("RESET app.tenant_id");
    } catch (SQLException ex) {
      log.warn("❌ Could not reset the tenant of a pooled connection", ex);
      throw ex;
    } finally {
      connection.close();
    }
  }

  // Hibernate may otherwise release and take a connection again between statements, and set the
  // tenant each time
  @Override
  public boolean supportsAggressiveRelease() {
    return false;
  }

  @Override
  public boolean isUnwrappableAs(Class<?> type) {
    return type.isInstance(this);
  }

  @Override
  public <T> T unwrap(Class<T> type) {
    if (!isUnwrappableAs(type)) {
      throw new IllegalArgumentException("Cannot unwrap " + getClass() + " as " + type);
    }
    return type.cast(this);
  }
}
