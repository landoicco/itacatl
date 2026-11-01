package dev.licaza.itacatl.tenant;

/**
 * The business whose data the current thread works with. Hibernate reads it when it opens a session
 * (see {@link TenantIdentifierResolver}) and adds {@code tenant_id = ?} to every query on a
 * tenant-owned entity. See docs/tenant-isolation.md.
 *
 * <p>A scope is opened with try-with-resources, or by {@link TenantScopeInterceptor} for a request,
 * and closing it restores the previous one:
 *
 * <pre>{@code
 * try (TenantContext.Scope scope = TenantContext.open(businessId)) {
 *   itemRepository.findAll(); // only this business's items
 * }
 * }</pre>
 */
public final class TenantContext {

  /**
   * Every business at once, for the public marketplace and the seed: queries are not filtered, and
   * a new row keeps the tenant it was given. Business IDs start at 1 ({@code ck_business_id}), so
   * it never names a real business.
   */
  public static final long CROSS_TENANT = 0L;

  /**
   * No scope opened: tenant-owned queries find nothing and inserts are rejected. Code that forgets
   * to open a scope fails closed instead of seeing every business.
   */
  public static final long NONE = -1L;

  // Server threads are reused across requests, so every scope must be closed (see Scope)
  private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static long current() {
    Long tenantId = CURRENT.get();
    return tenantId != null ? tenantId : NONE;
  }

  /** Makes {@code tenantId} current until the returned scope is closed. */
  public static Scope open(long tenantId) {
    Scope scope = new Scope(CURRENT.get());
    CURRENT.set(tenantId);
    return scope;
  }

  /** Restores the tenant that was current before {@link #open}, so scopes can nest. */
  public static final class Scope implements AutoCloseable {

    private final Long previous;

    private Scope(Long previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      // remove() rather than set(null), so a pooled thread keeps no entry at all
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }
}
