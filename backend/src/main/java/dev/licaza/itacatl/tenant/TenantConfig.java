package dev.licaza.itacatl.tenant;

import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.repo.BusinessRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Which tenant each API request works in, decided by its URL in one place and opened by a single
 * interceptor, so a request never gets two scopes. See docs/tenant-isolation.md.
 */
@Configuration
public class TenantConfig implements WebMvcConfigurer {

  // Management: only the business in the URL. Whether the user may manage it is decided later, by
  // @PreAuthorize on the service. The slug is read from this pattern, not from the controller's
  // mapping, so a controller that names its variable differently cannot change the tenant
  static final PathPattern MANAGEMENT = parse("/api/manage/businesses/{slug}/**");

  // The marketplace: every business's published menus, read across tenants on purpose
  static final List<PathPattern> MARKETPLACE =
      List.of(parse("/api/businesses/**"), parse("/api/menus/**"));

  // Looked up on first use, so web slice tests (@WebMvcTest), which have no repositories, can
  // still load this configuration
  private final ObjectProvider<BusinessRepository> businessRepository;

  public TenantConfig(ObjectProvider<BusinessRepository> businessRepository) {
    this.businessRepository = businessRepository;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new TenantScopeInterceptor(this::tenantOf)).addPathPatterns("/api/**");
  }

  /**
   * The business in a management URL; every business for reads of the marketplace; otherwise {@link
   * TenantContext#NONE}. Sign-in and the current user need no tenant: they only read accounts and
   * memberships, which belong to no business.
   */
  long tenantOf(HttpServletRequest request) {
    PathContainer path = path(request);

    PathPattern.PathMatchInfo management = MANAGEMENT.matchAndExtract(path);
    if (management != null) {
      return businessId(management.getUriVariables().get("slug"));
    }
    // Reads only: a future write under these paths (a customer's reservation) must not run with
    // the filter off, able to insert rows for any business
    if (isRead(request) && MARKETPLACE.stream().anyMatch(pattern -> pattern.matches(path))) {
      return TenantContext.CROSS_TENANT;
    }
    return TenantContext.NONE;
  }

  // An unknown slug stays NONE; the service then answers it as usual (403, or 404)
  private long businessId(String slug) {
    return businessRepository
        .getObject()
        .findBySlug(slug)
        .map(Business::getId)
        .orElse(TenantContext.NONE);
  }

  private static boolean isRead(HttpServletRequest request) {
    return HttpMethod.GET.matches(request.getMethod())
        || HttpMethod.HEAD.matches(request.getMethod());
  }

  // Spring MVC has usually parsed the path already to find the controller; reuse it if so
  private static PathContainer path(HttpServletRequest request) {
    return (ServletRequestPathUtils.hasParsedRequestPath(request)
            ? ServletRequestPathUtils.getParsedRequestPath(request)
            : ServletRequestPathUtils.parseAndCache(request))
        .pathWithinApplication();
  }

  private static PathPattern parse(String pattern) {
    return PathPatternParser.defaultInstance.parse(pattern);
  }
}
