package dev.licaza.itacatl;

import dev.licaza.itacatl.tenant.TenantContext;
import jakarta.servlet.Filter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Starts each MockMvc request as {@link TenantContext#NONE}, like a server thread, instead of in
 * the test thread's cross-tenant scope. Import it in full-application HTTP tests.
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
