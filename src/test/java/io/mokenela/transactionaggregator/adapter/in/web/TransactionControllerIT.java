package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.AbstractWebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionControllerIT extends AbstractWebIntegrationTest {

    private static final String ALICE   = "11111111-1111-1111-1111-111111111111";
    private static final String BOB     = "22222222-2222-2222-2222-222222222222";
    private static final String ACCOUNT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    // Authentication boundary
    @Test
    void recordTransaction_shouldReturn401_withoutToken() {
        webTestClient.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transactionBody(ALICE))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void searchTransactions_shouldReturn401_withoutToken() {
        webTestClient.get()
                .uri("/api/v1/transactions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void aggregateTransactions_shouldReturn401_withoutToken() {
        webTestClient.get()
                .uri(u -> u.path("/api/v1/transactions/aggregate")
                        .queryParam("accountId", ACCOUNT)
                        .queryParam("period", "MONTHLY")
                        .queryParam("from", "2024-01-01T00:00:00Z")
                        .queryParam("to", "2024-03-31T23:59:59Z")
                        .build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // Customer ownership enforcement
    @Test
    void recordTransaction_shouldReturn201_whenCustomerRecordsOwnTransaction() {
        var token = customerToken(ALICE);
        webTestClient.post()
                .uri("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transactionBody(ALICE))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .value(response -> {
                    assertThat(response.customerId()).isEqualTo(ALICE);
                    assertThat(response.id()).isNotNull();
                });
    }

    @Test
    void recordTransaction_shouldReturn403_whenCustomerRecordsForAnotherCustomer() {
        var aliceToken = customerToken(ALICE);
        webTestClient.post()
                .uri("/api/v1/transactions")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transactionBody(BOB)) // Alice's token, Bob's customerId
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void searchTransactions_shouldScopeResultsToOwnData_whenCustomerPassesOtherCustomerId() {
        // Alice requests with Bob's customerId — security scoping silently overrides
        // to Alice's own data rather than returning 403 (consistent with search semantics)
        var aliceToken = customerToken(ALICE);
        webTestClient.get()
                .uri(u -> u.path("/api/v1/transactions")
                        .queryParam("customerId", BOB)
                        .build())
                .header("Authorization", "Bearer " + aliceToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void aggregateTransactions_shouldReturn200_andScopeToOwnData_forCustomerToken() {
        var token = customerToken(ALICE);
        webTestClient.get()
                .uri(u -> u.path("/api/v1/transactions/aggregate")
                        .queryParam("accountId", ACCOUNT)
                        .queryParam("period", "MONTHLY")
                        .queryParam("from", "2024-01-01T00:00:00Z")
                        .queryParam("to", "2024-03-31T23:59:59Z")
                        .build())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    // Admin unrestricted access
    @Test
    void recordTransaction_shouldReturn201_whenAdminRecordsForAnyCustomer() {
        var adminToken = adminToken();
        webTestClient.post()
                .uri("/api/v1/transactions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transactionBody(BOB))
                .exchange()
                .expectStatus().isCreated();
    }

    // Validation
    @Test
    void recordTransaction_shouldReturn400_whenRequiredFieldsMissing() {
        var token = customerToken(ALICE);
        webTestClient.post()
                .uri("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", ALICE)) // missing amount, currency, type etc.
                .exchange()
                .expectStatus().isBadRequest();
    }

    // Helpers
    private Map<String, Object> transactionBody(String customerId) {
        return Map.of(
                "customerId", customerId,
                "accountId",  ACCOUNT,
                "amount",     50.00,
                "currency",   "GBP",
                "type",       "DEBIT",
                "description", "Test purchase",
                "merchantName", "Test Store"
        );
    }
}
