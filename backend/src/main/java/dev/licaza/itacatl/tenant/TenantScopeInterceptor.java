package dev.licaza.itacatl.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.ToLongFunction;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Opens a request's tenant scope before the controller runs, and so before the service's
 * transaction opens its Hibernate session, and closes it when the request completes. Which tenant
 * comes from the URL: see {@link TenantConfig}.
 */
public class TenantScopeInterceptor implements HandlerInterceptor {

  private static final String SCOPE = TenantScopeInterceptor.class.getName() + ".scope";

  private final ToLongFunction<HttpServletRequest> tenantOf;

  public TenantScopeInterceptor(ToLongFunction<HttpServletRequest> tenantOf) {
    this.tenantOf = tenantOf;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.setAttribute(SCOPE, TenantContext.open(tenantOf.applyAsLong(request)));
    return true;
  }

  // Runs after errors too, so a failed request never leaves its tenant on a pooled thread
  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (request.getAttribute(SCOPE) instanceof TenantContext.Scope scope) {
      scope.close();
    }
  }
}
