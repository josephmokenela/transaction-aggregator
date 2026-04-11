package io.mokenela.transactionaggregator.adapter.out.datasource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Reactive HTTP client for the Plaid sandbox API.
 * Handles token creation, exchange, and paginated transaction fetching.
 */
@Component
@ConditionalOnProperty(name = "app.plaid.enabled", havingValue = "true")
class PlaidClient {

    private static final Logger log = LoggerFactory.getLogger(PlaidClient.class);

    private final WebClient webClient;
    private final String clientId;
    private final String secret;
    private final String institutionId;

    PlaidClient(
            @Value("${app.plaid.base-url}") String baseUrl,
            @Value("${app.plaid.client-id}") String clientId,
            @Value("${app.plaid.secret}") String secret,
            @Value("${app.plaid.institution-id}") String institutionId) {
        var httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10));
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        this.clientId = clientId;
        this.secret = secret;
        this.institutionId = institutionId;
    }

    Mono<String> createSandboxPublicToken() {
        log.debug("Creating Plaid sandbox public token for institution={}", institutionId);
        return webClient.post()
                .uri("/sandbox/public_token/create")
                .bodyValue(new CreateSandboxTokenRequest(
                        clientId, secret, institutionId, List.of("transactions")))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), resp ->
                        resp.bodyToMono(String.class).flatMap(body -> {
                            log.error("Plaid createSandboxPublicToken error {}: {}", resp.statusCode(), body);
                            return Mono.error(new RuntimeException("Plaid error: " + body));
                        }))
                .bodyToMono(CreateSandboxTokenResponse.class)
                .map(CreateSandboxTokenResponse::publicToken);
    }

    Mono<String> exchangePublicToken(String publicToken) {
        log.debug("Exchanging Plaid public token for access token");
        return webClient.post()
                .uri("/item/public_token/exchange")
                .bodyValue(new ExchangeTokenRequest(clientId, secret, publicToken))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), resp ->
                        resp.bodyToMono(String.class).flatMap(body -> {
                            log.error("Plaid exchangePublicToken error {}: {}", resp.statusCode(), body);
                            return Mono.error(new RuntimeException("Plaid error: " + body));
                        }))
                .bodyToMono(ExchangeTokenResponse.class)
                .map(ExchangeTokenResponse::accessToken);
    }

    Mono<Void> refreshTransactions(String accessToken) {
        log.debug("Requesting Plaid transaction refresh for access token");
        return webClient.post()
                .uri("/transactions/refresh")
                .bodyValue(new RefreshTransactionsRequest(clientId, secret, accessToken))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), resp ->
                        resp.bodyToMono(String.class).flatMap(body -> {
                            log.warn("Plaid refreshTransactions error {}: {}", resp.statusCode(), body);
                            return Mono.error(new RuntimeException("Plaid error: " + body));
                        }))
                .bodyToMono(Void.class);
    }

    Mono<TransactionsPage> getTransactions(String accessToken, LocalDate from, LocalDate to,
                                           int count, int offset) {
        log.debug("Fetching Plaid transactions from={} to={} offset={}", from, to, offset);
        return webClient.post()
                .uri("/transactions/get")
                .bodyValue(new GetTransactionsRequest(
                        clientId, secret, accessToken,
                        from.toString(), to.toString(),
                        new TransactionOptions(count, offset)))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), resp ->
                        resp.bodyToMono(String.class).flatMap(body -> {
                            log.error("Plaid getTransactions error {}: {}", resp.statusCode(), body);
                            if (body.contains("PRODUCT_NOT_READY")) {
                                return Mono.error(new ProductNotReadyException(body));
                            }
                            if (body.contains("INVALID_ACCESS_TOKEN")) {
                                return Mono.error(new InvalidAccessTokenException(body));
                            }
                            return Mono.error(new RuntimeException("Plaid error: " + body));
                        }))
                .bodyToMono(TransactionsPage.class);
    }

    static final class ProductNotReadyException extends RuntimeException {
        ProductNotReadyException(String body) { super("Plaid PRODUCT_NOT_READY: " + body); }
    }

    static final class InvalidAccessTokenException extends RuntimeException {
        InvalidAccessTokenException(String body) { super("Plaid INVALID_ACCESS_TOKEN: " + body); }
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    record CreateSandboxTokenRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("secret") String secret,
            @JsonProperty("institution_id") String institutionId,
            @JsonProperty("initial_products") List<String> initialProducts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateSandboxTokenResponse(
            @JsonProperty("public_token") String publicToken) {}

    record ExchangeTokenRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("secret") String secret,
            @JsonProperty("public_token") String publicToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExchangeTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("item_id") String itemId) {}

    record RefreshTransactionsRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("secret") String secret,
            @JsonProperty("access_token") String accessToken) {}

    record GetTransactionsRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("secret") String secret,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("start_date") String startDate,
            @JsonProperty("end_date") String endDate,
            @JsonProperty("options") TransactionOptions options) {}

    record TransactionOptions(
            @JsonProperty("count") int count,
            @JsonProperty("offset") int offset) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransactionsPage(
            @JsonProperty("transactions") List<PlaidTransaction> transactions,
            @JsonProperty("total_transactions") int totalTransactions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlaidTransaction(
            @JsonProperty("transaction_id") String transactionId,
            @JsonProperty("account_id") String accountId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("iso_currency_code") String isoCurrencyCode,
            @JsonProperty("name") String name,
            @JsonProperty("merchant_name") String merchantName,
            @JsonProperty("date") String date,
            @JsonProperty("pending") boolean pending,
            @JsonProperty("category") List<String> category) {}
}
