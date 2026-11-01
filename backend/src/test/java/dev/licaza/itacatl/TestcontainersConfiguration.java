package dev.licaza.itacatl;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL for test slices ({@code @DataJpaTest}). They connect as the tables' owner, which
 * Row-Level Security does not apply to, so fixtures can build several businesses in one
 * transaction. Full-application tests use {@link AppRoleTestcontainersConfiguration} instead.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return postgres();
  }

  // Same image as docker-compose, so tests run against the real database engine, with the
  // application's role created before the migrations grant it privileges
  static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
        .withInitScript("testcontainers/create-app-role.sql");
  }
}
