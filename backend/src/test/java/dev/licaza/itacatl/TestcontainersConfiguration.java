package dev.licaza.itacatl;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL for test slices, connected as the owner, whom Row-Level Security does not apply to.
 * Full-application tests use {@link AppRoleTestcontainersConfiguration}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return postgres();
  }

  // Same image as docker-compose, with the application's role created before the migrations
  static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
        .withInitScript("testcontainers/create-app-role.sql");
  }
}
