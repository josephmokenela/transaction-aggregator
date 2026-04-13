package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.AbstractWebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends AbstractWebIntegrationTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";

    @Test
    void customerToken_shouldIssueSignedToken_forValidCustomerId() {
        webTestClient.post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", ALICE))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .value(response -> {
                    assertThat(response.token()).isNotBlank();
                    assertThat(response.type()).isEqualTo("Bearer");
                    assertThat(response.expiresIn()).isEqualTo(86400L);
                });
    }

    @Test
    void customerToken_shouldReturn400_whenCustomerIdIsBlank() {
        webTestClient.post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", ""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void adminToken_shouldIssueSignedToken_forCorrectSecret() {
        webTestClient.post()
                .uri("/api/v1/auth/admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("secret", "dev-only-admin-secret"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .value(response -> assertThat(response.token()).isNotBlank());
    }

    @Test
    void adminToken_shouldReturn401_forWrongSecret() {
        webTestClient.post()
                .uri("/api/v1/auth/admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("secret", "wrong-secret"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authEndpoints_shouldBeAccessible_withoutToken() {
        // auth endpoints must be publicly reachable — no 401 on the endpoint itself
        webTestClient.post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", ALICE))
                .exchange()
                .expectStatus().isOk(); // not 401
    }
}
