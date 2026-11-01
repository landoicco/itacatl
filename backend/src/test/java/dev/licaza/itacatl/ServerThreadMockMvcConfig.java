package dev.licaza.itacatl;

import dev.licaza.itacatl.tenant.TenantContext;
import jakarta.servlet.Filter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * MockMvc runs a request on the test's own thread, which is in the cross-tenant scope
 * (CrossTenantTestExecutionListener). This filter runs each request as {@link TenantContext#NONE}
 * instead, like a server thread, so an endpoint only sees the tenant data its URL rule in
 * TenantConfig gives it, in tests as in production. Import it in full-application HTTP tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ServerThreadMockMvcConfig {

  @Bean
  MockMvcBuilderCustomizer serverThreadTenantScope() {
    Filter noTenant =
        (request, response, chain) -> {
          try (TenantContext.Scope scope = TenantContext.open(TenantContext.NONE)) {
            chain.doFilter(request, response);
          }
        };
    return builder -> builder.addFilters(noTenant);
  }
}
