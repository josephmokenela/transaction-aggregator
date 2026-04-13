package io.mokenela.transactionaggregator;

import io.mokenela.transactionaggregator.adapter.in.web.TokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for controller integration tests.
 *
 * <p>Starts a full Spring application context on a random port with a real PostgreSQL
 * instance (Testcontainers). The static container is shared across all subclasses in
 * the same JVM lifecycle to avoid repeated cold starts.
 *
 * <p>Helper methods {@link #customerToken} and {@link #adminToken} obtain real JWTs
 * by calling the auth endpoint — this tests the full token issuance path, not a mock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractWebIntegrationTest {

    // Started in a static initialiser rather than via @Container/@Testcontainers so the
    // container is never stopped by the JUnit extension when a test class completes. With
    // @Container on a static superclass field, Testcontainers' afterAll callback stops
    // the container after each subclass finishes, which invalidates the R2DBC URL cached
    // in the shared Spring context and causes "Connection refused" in later test classes.
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

        // Use a predictable test secret so AuthControllerIT can send it directly.
        // The production default ("local-dev-admin-do-not-use-in-docker-or-prod") is
        // overridden here so tests never need to know the real default value.
        registry.add("app.security.jwt.admin-secret", () -> "dev-only-admin-secret");
    }

    @Autowired
    protected WebTestClient webTestClient;

    // Tokens are cached per (type × customerId) for the lifetime of the static Spring context.
    // AuthController has a static rate limiter (10 req/min); fetching a fresh token on every
    // test method would exhaust it when a test suite makes more than 10 auth calls.
    private static final Map<String, String> TOKEN_CACHE = new ConcurrentHashMap<>();

    /**
     * Issues (or returns a cached) customer JWT for the given customerId.
     * The token subject is set to the customerId UUID — ownership checks in controllers
     * use this subject to scope access.
     */
    protected String customerToken(String customerId) {
        return TOKEN_CACHE.computeIfAbsent("customer:" + customerId, k ->
                Objects.requireNonNull(
                        webTestClient.post()
                                .uri("/api/v1/auth/token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("customerId", customerId))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(TokenResponse.class)
                                .returnResult()
                                .getResponseBody()
                ).token());
    }

    /**
     * Issues (or returns a cached) admin JWT using the default dev admin secret.
     * Admin tokens carry both ROLE_ADMIN and ROLE_CUSTOMER.
     */
    protected String adminToken() {
        return TOKEN_CACHE.computeIfAbsent("admin", k ->
                Objects.requireNonNull(
                        webTestClient.post()
                                .uri("/api/v1/auth/admin-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("secret", "dev-only-admin-secret"))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(TokenResponse.class)
                                .returnResult()
                                .getResponseBody()
                ).token());
    }
}
