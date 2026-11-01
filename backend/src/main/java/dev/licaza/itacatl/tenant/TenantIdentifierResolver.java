package dev.licaza.itacatl.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which business a new session belongs to, from {@link TenantContext}. Hibernate
 * asks once, when the session opens (with a transaction, since Open Session in View is off), and
 * keeps the answer for the whole session. See docs/tenant-isolation.md.
 *
 * <p>Registered by class name in application.properties rather than as a Spring bean: it has no
 * dependencies, and the property also reaches test slices such as {@code @DataJpaTest}, which do
 * not scan components.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long> {

  @Override
  public Long resolveCurrentTenantIdentifier() {
    return TenantContext.current();
  }

  // Only matters for SessionFactory.getCurrentSession(), which Spring does not use
  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }

  // The root tenant skips the tenant filter: Hibernate's own hook for cross-tenant access
  @Override
  public boolean isRoot(Long tenantId) {
    return tenantId == TenantContext.CROSS_TENANT;
  }
}
