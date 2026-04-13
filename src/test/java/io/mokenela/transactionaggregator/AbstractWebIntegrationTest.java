package io.mokenela.transactionaggregator;

import io.mokenela.transactionaggregator.adapter.in.web.TokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Objects;

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
@Testcontainers
public abstract class AbstractWebIntegrationTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    protected WebTestClient webTestClient;

    /**
     * Issues a real customer JWT for the given customerId via the auth endpoint.
     * The token subject is set to the customerId UUID — ownership checks in controllers
     * use this subject to scope access.
     */
    protected String customerToken(String customerId) {
        return Objects.requireNonNull(
                webTestClient.post()
                        .uri("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("customerId", customerId))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(TokenResponse.class)
                        .returnResult()
                        .getResponseBody()
        ).token();
    }

    /**
     * Issues a real admin JWT using the default dev admin secret.
     * Admin tokens carry both ROLE_ADMIN and ROLE_CUSTOMER.
     */
    protected String adminToken() {
        return Objects.requireNonNull(
                webTestClient.post()
                        .uri("/api/v1/auth/admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("secret", "dev-only-admin-secret"))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(TokenResponse.class)
                        .returnResult()
                        .getResponseBody()
        ).token();
    }
}
