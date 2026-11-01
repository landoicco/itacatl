package dev.licaza.itacatl;

import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL for full-application tests, connected like production: Flyway as the owner, the
 * application as itacatl_app, subject to Row-Level Security.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AppRoleTestcontainersConfiguration {

  // Only Flyway's connection: without the type, the application would connect as the owner too
  @Bean
  @ServiceConnection(type = FlywayConnectionDetails.class)
  PostgreSQLContainer postgresContainer() {
    return TestcontainersConfiguration.postgres();
  }

  // Password from testcontainers/create-app-role.sql
  @Bean
  DynamicPropertyRegistrar appRoleConnection(PostgreSQLContainer postgres) {
    return registry -> {
      registry.add("spring.datasource.url", postgres::getJdbcUrl);
      registry.add("spring.datasource.username", () -> "itacatl_app");
      registry.add("spring.datasource.password", () -> "itacatl-app-test");
    };
  }
}
