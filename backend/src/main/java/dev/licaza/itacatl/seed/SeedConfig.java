package dev.licaza.itacatl.seed;

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
      MarketplaceSeeder seeder, @Value("${itacatl.seed.file}") Resource file) {
    return args -> seeder.seed(file);
  }
}
