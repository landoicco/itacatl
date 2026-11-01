package dev.licaza.itacatl.seed;

import dev.licaza.itacatl.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

// Off by default, so production never gets sample data by accident; the local profile enables it
@Configuration
@ConditionalOnProperty(name = "itacatl.seed.enabled", havingValue = "true")
public class SeedConfig {

  @Bean
  ApplicationRunner marketplaceSeedRunner(
      MarketplaceSeeder seeder,
      @Value("${itacatl.seed.file}") Resource file,
      // No default: a deployment that enables the seed must choose the demo password
      @Value("${itacatl.seed.user-password:}") String userPassword) {
    return args -> {
      // Creates rows for several businesses in one transaction, each with its tenant set
      // explicitly. Opened here because the scope must exist before the transaction starts
      try (TenantContext.Scope scope = TenantContext.open(TenantContext.CROSS_TENANT)) {
        seeder.seed(file, userPassword);
      }
    };
  }
}
