package dev.licaza.itacatl;

import dev.licaza.itacatl.database.AppRoleCallback;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL for test slices, connected as the owner, whom Row-Level Security does not apply to.
 * Full-application tests use {@link AppRoleTestcontainersConfiguration}.
 */
// Slices do not scan components, and the migrations need the role the callback creates
@TestConfiguration(proxyBeanMethods = false)
@Import(AppRoleCallback.class)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return postgres();
  }

  // Same image as docker-compose
  static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
  }
}
