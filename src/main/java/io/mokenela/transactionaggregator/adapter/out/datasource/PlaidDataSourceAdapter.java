package io.mokenela.transactionaggregator.adapter.out.datasource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.FetchTransactionsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches real (sandbox) transactions from Plaid via its REST API.
 *
 * <p>Token lifecycle:
 * <ol>
 *   <li>On first call, creates a sandbox Item (public token) for the configured institution.</li>
 *   <li>Exchanges the public token for an access token and caches it in memory.</li>
 *   <li>Uses the cached access token for all subsequent calls.</li>
 * </ol>
 *
 * <p>Pagination: Plaid returns up to 500 transactions per page. This adapter
 * expands pages reactively until all transactions are fetched.
 */
@Component
@ConditionalOnProperty(name = "app.plaid.enabled", havingValue = "true")
class PlaidDataSourceAdapter implements FetchTransactionsPort {

    private static final Logger log = LoggerFactory.getLogger(PlaidDataSourceAdapter.class);
    private static final DataSourceId SOURCE_ID = DataSourceId.PLAID;
    private static final int PAGE_SIZE = 500;
    private static final Duration TOKEN_TTL = Duration.ofHours(23);

    private record TokenEntry(String token, Instant cachedAt) {
        boolean isExpired() { return Instant.now().isAfter(cachedAt.plus(TOKEN_TTL)); }
    }

    private final PlaidClient plaidClient;
    private final MeterRegistry meterRegistry;
    private final AtomicReference<TokenEntry> tokenCache = new AtomicReference<>();
    private final CircuitBreaker circuitBreaker;

    PlaidDataSourceAdapter(PlaidClient plaidClient, MeterRegistry meterRegistry) {
        this.plaidClient = plaidClient;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = CircuitBreaker.of("plaid", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(8))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .ignoreExceptions(PlaidClient.ProductNotReadyException.class)
                .build());
        // Pre-register a counter for every possible state so the metric exists in
        // Prometheus from startup, not only after the first transition fires.
        for (var state : CircuitBreaker.State.values()) {
            meterRegistry.counter("plaid.circuit.transitions", "to", state.name());
        }
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(e -> {
                    log.warn("Plaid circuit breaker state: {} -> {}",
                            e.getStateTransition().getFromState(),
                            e.getStateTransition().getToState());
                    meterRegistry.counter("plaid.circuit.transitions",
                            "to", e.getStateTransition().getToState().name()).increment();
                });
    }

    @Override
    public DataSourceId sourceId() {
        return SOURCE_ID;
    }

    @Override
    public Flux<Transaction> fetchTransactions(CustomerId customerId, Instant from, Instant to) {
        var fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        var toDate = to.atZone(ZoneOffset.UTC).toLocalDate();

        return getAccessToken()
                .flatMapMany(token -> fetchAllPages(token, fromDate, toDate)
                        .onErrorResume(PlaidClient.InvalidAccessTokenException.class, ex -> {
                            log.warn("Plaid access token invalidated — clearing cache and retrying");
                            tokenCache.set(null);
                            meterRegistry.counter("plaid.token.evictions").increment();
                            return getAccessToken()
                                    .flatMapMany(freshToken -> fetchAllPages(freshToken, fromDate, toDate));
                        }))
                .map(t -> toDomain(t, customerId))
                .doOnError(ex -> log.error("Plaid fetch failed: {}", ex.getMessage(), ex));
    }

    // Private helpers
    private Mono<String> getAccessToken() {
        var cached = tokenCache.get();
        if (cached != null && !cached.isExpired()) return Mono.just(cached.token());

        if (cached != null) log.info("Plaid access token expired after {} hours — refreshing", TOKEN_TTL.toHours());

        return plaidClient.createSandboxPublicToken()
                .flatMap(plaidClient::exchangePublicToken)
                .flatMap(token -> {
                    tokenCache.set(new TokenEntry(token, Instant.now()));
                    meterRegistry.counter("plaid.token.refreshes").increment();
                    log.info("Plaid sandbox access token obtained and cached — requesting transaction refresh");
                    return plaidClient.refreshTransactions(token)
                            .doOnSuccess(v -> log.info("Plaid transaction refresh requested"))
                            .onErrorResume(ex -> {
                                log.warn("Plaid refresh request failed (non-fatal): {}", ex.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(token);
                });
    }

    private Flux<PlaidClient.PlaidTransaction> fetchAllPages(String accessToken,
                                                             LocalDate from, LocalDate to) {
        record PageState(List<PlaidClient.PlaidTransaction> transactions, int nextOffset, int total) {}

        var timer = io.micrometer.core.instrument.Timer.builder("plaid.fetch.duration")
                .publishPercentileHistogram()
                .register(meterRegistry);
        var sample = io.micrometer.core.instrument.Timer.start(meterRegistry);

        return plaidClient.getTransactions(accessToken, from, to, PAGE_SIZE, 0)
                        .retryWhen(reactor.util.retry.Retry.backoff(5, Duration.ofSeconds(3))
                                .filter(ex -> ex instanceof PlaidClient.ProductNotReadyException)
                                .doBeforeRetry(sig -> log.info("Plaid PRODUCT_NOT_READY — retrying (attempt {})",
                                        sig.totalRetriesInARow() + 1)))
                        .doOnSuccess(p -> sample.stop(timer))
                        .doOnError(ex -> sample.stop(timer))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .map(page -> new PageState(page.transactions(), page.transactions().size(), page.totalTransactions()))
                .expand(state -> {
                    if (state.nextOffset() >= state.total()) return Mono.empty();
                    return plaidClient.getTransactions(accessToken, from, to, PAGE_SIZE, state.nextOffset())
                            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                            .map(page -> new PageState(
                                    page.transactions(),
                                    state.nextOffset() + page.transactions().size(),
                                    state.total()));
                })
                .flatMap(state -> Flux.fromIterable(state.transactions()));
    }

    private Transaction toDomain(PlaidClient.PlaidTransaction t, CustomerId customerId) {
        // Plaid: positive amount = money out (debit), negative = money in (credit)
        var amount = t.amount().abs();
        var type = t.amount().compareTo(BigDecimal.ZERO) > 0
                ? TransactionType.DEBIT : TransactionType.CREDIT;
        var status = t.pending() ? TransactionStatus.PENDING : TransactionStatus.COMPLETED;
        var currency = t.isoCurrencyCode() != null ? t.isoCurrencyCode() : "USD";
        var occurredAt = LocalDate.parse(t.date()).atStartOfDay(ZoneOffset.UTC).toInstant();

        return new Transaction(
                new TransactionId(deterministicUUID(t.transactionId())),
                customerId,
                new AccountId(deterministicUUID(t.accountId())),
                Money.of(amount, currency),
                type,
                status,
                t.name(),
                mapCategory(t.category()),
                t.merchantName(),
                SOURCE_ID,
                occurredAt
        );
    }

    private TransactionCategory mapCategory(List<String> categories) {
        if (categories == null || categories.isEmpty()) return TransactionCategory.OTHER;
        return switch (categories.getFirst()) {
            case "Food and Drink"                       -> TransactionCategory.FOOD_AND_DINING;
            case "Transportation", "Travel"             -> TransactionCategory.TRANSPORT;
            case "Shops"                                -> TransactionCategory.SHOPPING;
            case "Recreation"                           -> TransactionCategory.ENTERTAINMENT;
            case "Healthcare", "Medical"                -> TransactionCategory.HEALTHCARE;
            case "Payment", "Transfer"                  -> TransactionCategory.TRANSFER;
            case "Education"                            -> TransactionCategory.EDUCATION;
            case "Insurance"                            -> TransactionCategory.INSURANCE;
            case "Service", "Bank Fees"                 -> TransactionCategory.OTHER;
            default                                     -> TransactionCategory.OTHER;
        };
    }

    private UUID deterministicUUID(String plaidId) {
        return UUID.nameUUIDFromBytes(plaidId.getBytes(StandardCharsets.UTF_8));
    }
}
