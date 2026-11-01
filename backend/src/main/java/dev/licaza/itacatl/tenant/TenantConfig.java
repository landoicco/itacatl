package dev.licaza.itacatl.tenant;

import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.repo.BusinessRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Which tenant each API request works in, decided by its URL in one place. Requests that match no
 * pattern (sign-in, the current user) stay in {@link TenantContext#NONE}: they only read accounts
 * and memberships, which belong to no business. See docs/tenant-isolation.md.
 */
@Configuration
public class TenantConfig implements WebMvcConfigurer {

  private final BusinessRepository businessRepository;

  public TenantConfig(BusinessRepository businessRepository) {
    this.businessRepository = businessRepository;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // The marketplace: every business's published menus, read across tenants on purpose
    registry
        .addInterceptor(new TenantScopeInterceptor(request -> TenantContext.CROSS_TENANT))
        .addPathPatterns("/api/businesses/**", "/api/menus/**");
    // Management: only the business in the URL. Whether the user may manage it is decided later,
    // by @PreAuthorize on the service
    registry
        .addInterceptor(new TenantScopeInterceptor(this::businessInUrl))
        .addPathPatterns("/api/manage/businesses/{slug}/**");
  }

  // An unknown slug stays NONE; the service then answers it as usual (403, or 404)
  private long businessInUrl(HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Map<String, String> variables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    String slug = variables != null ? variables.get("slug") : null;
    if (slug == null) {
      return TenantContext.NONE;
    }
    return businessRepository.findBySlug(slug).map(Business::getId).orElse(TenantContext.NONE);
  }
}
