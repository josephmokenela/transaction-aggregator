package io.mokenela.transactionaggregator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that require a running PostgreSQL instance.
 *
 * <p>The container is declared {@code static} so Testcontainers reuses the same instance
 * across all tests in the JVM lifecycle — significantly faster than starting a new
 * container per test class.</p>
 *
 * <p>{@code @ServiceConnection} wires auto-configured beans (DataSource, ConnectionFactory)
 * to the container. {@code @DynamicPropertySource} additionally overrides the raw property
 * values so that {@code PostgresConfig}, which reads {@code @Value("${spring.datasource.url}")}
 * rather than injected {@code ConnectionDetails} beans, also connects to the container.
 * It also disables {@code spring-boot-docker-compose} so the compose.yaml at the project
 * root is not started during tests.</p>
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    // Static initialiser pattern — see AbstractWebIntegrationTest for rationale.
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        // Disable spring-boot-docker-compose — compose.yaml requires .env which is not
        // present in CI or local test runs, and the test container replaces it anyway.
        registry.add("spring.docker.compose.enabled", () -> "false");

        // PostgresConfig reads these via @Value, so they must be present in the Spring
        // Environment (not just in ConnectionDetails beans used by auto-configuration).
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost()
                        + ":" + postgres.getMappedPort(5432)
                        + "/" + postgres.getDatabaseName());
    }
}
