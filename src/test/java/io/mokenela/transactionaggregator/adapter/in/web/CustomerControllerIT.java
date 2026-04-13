package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.AbstractWebIntegrationTest;
import io.mokenela.transactionaggregator.domain.port.in.PagedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerControllerIT extends AbstractWebIntegrationTest {

    private static final String ALICE      = "11111111-1111-1111-1111-111111111111";
    private static final String BOB        = "22222222-2222-2222-2222-222222222222";
    private static final String DATE_RANGE = "?from=2024-01-01T00:00:00Z&to=2024-12-31T23:59:59Z";

    // ── GET /api/v1/customers (admin only) ─────────────────────────────────────

    @Test
    void listCustomers_shouldReturn401_withoutToken() {
        webTestClient.get()
                .uri("/api/v1/customers")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listCustomers_shouldReturn403_forCustomerToken() {
        var token = customerToken(ALICE);
        webTestClient.get()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void listCustomers_shouldReturn200_forAdminToken_withPagedResponse() {
        var token = adminToken();
        webTestClient.get()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<CustomerResponse>>() {})
                .value(paged -> {
                    assertThat(paged).isNotNull();
                    assertThat(paged.content()).isNotEmpty();
                    assertThat(paged.page()).isZero();
                    assertThat(paged.size()).isEqualTo(20);
                    assertThat(paged.totalElements()).isPositive();
                    assertThat(paged.totalPages()).isPositive();
                });
    }

    @Test
    void listCustomers_shouldRespectPageAndSizeParams() {
        var token = adminToken();
        webTestClient.get()
                .uri("/api/v1/customers?page=0&size=1")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<CustomerResponse>>() {})
                .value(paged -> {
                    assertThat(paged.content()).hasSize(1);
                    assertThat(paged.size()).isEqualTo(1);
                });
    }

    @Test
    void listCustomers_shouldReturn400_whenSizeExceedsMax() {
        var token = adminToken();
        webTestClient.get()
                .uri("/api/v1/customers?size=101")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listCustomers_shouldReturn400_whenPageIsNegative() {
        var token = adminToken();
        webTestClient.get()
                .uri("/api/v1/customers?page=-1")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── GET /api/v1/customers/{id}/summary ─────────────────────────────────────

    @Test
    void getCustomerSummary_shouldReturn401_withoutToken() {
        webTestClient.get()
                .uri("/api/v1/customers/" + ALICE + "/summary" + DATE_RANGE)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getCustomerSummary_shouldReturn200_whenCustomerAccessesOwnSummary() {
        var token = customerToken(ALICE);
        webTestClient.get()
                .uri("/api/v1/customers/" + ALICE + "/summary" + DATE_RANGE)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getCustomerSummary_shouldReturn403_whenCustomerAccessesAnotherCustomersSummary() {
        var aliceToken = customerToken(ALICE);
        webTestClient.get()
                .uri("/api/v1/customers/" + BOB + "/summary" + DATE_RANGE)
                .header("Authorization", "Bearer " + aliceToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getCustomerSummary_shouldReturn200_whenAdminAccessesAnyCustomer() {
        var token = adminToken();
        webTestClient.get()
                .uri("/api/v1/customers/" + BOB + "/summary" + DATE_RANGE)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    // ── GET /api/v1/customers/{id}/categories ──────────────────────────────────

    @Test
    void getCategoryBreakdown_shouldReturn403_whenCustomerAccessesAnotherCustomersData() {
        var aliceToken = customerToken(ALICE);
        webTestClient.get()
                .uri("/api/v1/customers/" + BOB + "/categories" + DATE_RANGE)
                .header("Authorization", "Bearer " + aliceToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getCategoryBreakdown_shouldReturn200_whenCustomerAccessesOwnData() {
        var token = customerToken(ALICE);
        webTestClient.get()
                .uri("/api/v1/customers/" + ALICE + "/categories" + DATE_RANGE)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    // ── GET /api/v1/customers/{id}/transactions ─────────────────────────────────

    @Test
    void getCustomerTransactions_shouldReturn403_whenCustomerAccessesAnotherCustomersData() {
        var aliceToken = customerToken(ALICE);
        webTestClient.get()
                .uri("/api/v1/customers/" + BOB + "/transactions")
                .header("Authorization", "Bearer " + aliceToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getCustomerTransactions_shouldReturn200_whenCustomerAccessesOwnData() {
        var token = customerToken(ALICE);
        webTestClient.get()
                .uri("/api/v1/customers/" + ALICE + "/transactions")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    // ── concurrency ────────────────────────────────────────────────────────────

    @Test
    void getCustomerSummary_shouldReturn200_forAllConcurrentRequests() throws Exception {
        var token = customerToken(ALICE);
        var uri   = "/api/v1/customers/" + ALICE + "/summary" + DATE_RANGE;

        // Fire 10 requests in parallel using virtual threads (Java 21).
        // Verifies the reactive pipeline handles concurrent load without data races,
        // connection pool exhaustion, or circuit breaker false-positives.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, 10)
                    .mapToObj(i -> executor.submit(() ->
                            webTestClient.get()
                                    .uri(uri)
                                    .header("Authorization", "Bearer " + token)
                                    .exchange()
                                    .expectStatus().isOk()
                    ))
                    .toList();

            for (var future : futures) {
                future.get(); // propagates any assertion error from the worker thread
            }
        }
    }
}
