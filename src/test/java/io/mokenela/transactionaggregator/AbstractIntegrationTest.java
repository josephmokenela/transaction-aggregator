package io.mokenela.transactionaggregator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that require a running PostgreSQL instance.
 *
 * <p>The container is declared {@code static} so Testcontainers reuses the same instance
 * across all tests in the JVM lifecycle — significantly faster than starting a new
 * container per test class.</p>
 *
 * <p>{@code @ServiceConnection} auto-wires both the R2DBC URL and the JDBC DataSource URL
 * (used by Flyway), so no manual property overrides are needed.</p>
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");
}
