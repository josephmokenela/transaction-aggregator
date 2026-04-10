package io.mokenela.transactionaggregator.adapter.out.datasource;

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

    private final PlaidClient plaidClient;
    private final AtomicReference<String> accessTokenCache = new AtomicReference<>();

    PlaidDataSourceAdapter(PlaidClient plaidClient) {
        this.plaidClient = plaidClient;
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
                .flatMapMany(token -> fetchAllPages(token, fromDate, toDate))
                .map(t -> toDomain(t, customerId))
                .doOnError(ex -> log.error("Plaid fetch failed: {}", ex.getMessage(), ex));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Mono<String> getAccessToken() {
        var cached = accessTokenCache.get();
        if (cached != null) return Mono.just(cached);

        return plaidClient.createSandboxPublicToken()
                .flatMap(plaidClient::exchangePublicToken)
                .flatMap(token -> {
                    accessTokenCache.set(token);
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

        return plaidClient.getTransactions(accessToken, from, to, PAGE_SIZE, 0)
                .retryWhen(reactor.util.retry.Retry.backoff(5, Duration.ofSeconds(3))
                        .filter(ex -> ex instanceof PlaidClient.ProductNotReadyException)
                        .doBeforeRetry(sig -> log.info("Plaid PRODUCT_NOT_READY — retrying in ~{}s (attempt {})",
                                3 * (1 << sig.totalRetriesInARow()), sig.totalRetriesInARow() + 1)))
                .map(page -> new PageState(page.transactions(), page.transactions().size(), page.totalTransactions()))
                .expand(state -> {
                    if (state.nextOffset() >= state.total()) return Mono.empty();
                    return plaidClient.getTransactions(accessToken, from, to, PAGE_SIZE, state.nextOffset())
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
