package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.AbstractIntegrationTest;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the R2DBC persistence adapters.
 *
 * <p>Extends {@link AbstractIntegrationTest} — a single PostgreSQL container is shared
 * across all integration test classes in the JVM lifecycle (Testcontainers reuse).
 * Flyway migrations run automatically on startup, seeding the demo customers.</p>
 */
class TransactionPersistenceAdapterIT extends AbstractIntegrationTest {

    private static final CustomerId CUSTOMER_ID =
            new CustomerId(UUID.fromString("11111111-1111-1111-1111-111111111111")); // seeded by V2
    private static final AccountId ACCOUNT_ID = new AccountId(UUID.randomUUID());

    @Autowired
    SaveTransactionPort saveTransactionPort;

    @Autowired
    LoadTransactionPort loadTransactionPort;

    // ── save + loadById ────────────────────────────────────────────────────────

    @Test
    void save_shouldPersistTransaction_andLoadById_shouldReturnIt() {
        var tx = sampleTransaction(TransactionType.CREDIT, "1500.00", TransactionCategory.SALARY);

        StepVerifier.create(saveTransactionPort.save(tx).then(loadTransactionPort.loadById(tx.id())))
                .assertNext(loaded -> {
                    assertThat(loaded.id()).isEqualTo(tx.id());
                    assertThat(loaded.amount().amount()).isEqualByComparingTo("1500.00");
                    assertThat(loaded.category()).isEqualTo(TransactionCategory.SALARY);
                    assertThat(loaded.type()).isEqualTo(TransactionType.CREDIT);
                    assertThat(loaded.dataSourceId()).isEqualTo(DataSourceId.MANUAL);
                })
                .verifyComplete();
    }

    @Test
    void loadById_shouldReturnEmpty_whenTransactionDoesNotExist() {
        StepVerifier.create(loadTransactionPort.loadById(TransactionId.generate()))
                .verifyComplete(); // empty Mono — not an error
    }

    // ── loadByAccountIdAndPeriod ───────────────────────────────────────────────

    @Test
    void loadByAccountIdAndPeriod_shouldReturnOnlyTransactionsWithinWindow() {
        var accountId = new AccountId(UUID.randomUUID());
        var base = Instant.parse("2024-06-15T12:00:00Z");

        var inside = sampleTransactionAt(accountId, TransactionType.DEBIT, "50.00",
                TransactionCategory.FOOD_AND_DINING, base);
        var outside = sampleTransactionAt(accountId, TransactionType.DEBIT, "20.00",
                TransactionCategory.TRANSPORT, base.plus(40, ChronoUnit.DAYS));

        var from = base.minus(1, ChronoUnit.DAYS);
        var to   = base.plus(7, ChronoUnit.DAYS);

        StepVerifier.create(
                saveTransactionPort.save(inside)
                        .then(saveTransactionPort.save(outside))
                        .thenMany(loadTransactionPort.loadByAccountIdAndPeriod(accountId, from, to))
        )
                .assertNext(tx -> assertThat(tx.id()).isEqualTo(inside.id()))
                .verifyComplete();
    }

    // ── loadByFilter ──────────────────────────────────────────────────────────

    @Test
    void loadByFilter_shouldFilterByCategory() {
        var accountId = new AccountId(UUID.randomUUID());
        var salary = sampleTransactionAt(accountId, TransactionType.CREDIT, "3500.00",
                TransactionCategory.SALARY, Instant.now());
        var shopping = sampleTransactionAt(accountId, TransactionType.DEBIT, "120.00",
                TransactionCategory.SHOPPING, Instant.now());

        var filter = TransactionFilter.builder()
                .accountId(accountId)
                .category(TransactionCategory.SALARY)
                .build();

        StepVerifier.create(
                saveTransactionPort.save(salary)
                        .then(saveTransactionPort.save(shopping))
                        .thenMany(loadTransactionPort.loadByFilter(filter, 50))
        )
                .assertNext(tx -> assertThat(tx.category()).isEqualTo(TransactionCategory.SALARY))
                .verifyComplete();
    }

    @Test
    void save_shouldBeIdempotent_onRepeatedSave() {
        var tx = sampleTransaction(TransactionType.CREDIT, "100.00", TransactionCategory.OTHER);

        StepVerifier.create(
                saveTransactionPort.save(tx)
                        .then(saveTransactionPort.save(tx)) // re-save same ID
                        .then(loadTransactionPort.loadById(tx.id()))
        )
                .assertNext(loaded -> assertThat(loaded.id()).isEqualTo(tx.id()))
                .verifyComplete();
    }

    @Test
    void save_shouldBeIdempotent_underConcurrentSaves() {
        var tx = sampleTransaction(TransactionType.CREDIT, "250.00", TransactionCategory.SALARY);

        // Fire 5 concurrent saves of the identical transaction — the upsert constraint
        // must ensure exactly one row exists regardless of interleaving.
        StepVerifier.create(
                Flux.range(0, 5)
                        .flatMap(i -> saveTransactionPort.save(tx))
                        .then(loadTransactionPort.loadById(tx.id()))
        )
                .assertNext(loaded -> {
                    assertThat(loaded.id()).isEqualTo(tx.id());
                    assertThat(loaded.amount().amount()).isEqualByComparingTo("250.00");
                })
                .verifyComplete();
    }

    // ── aggregateByFilter ─────────────────────────────────────────────────────

    @Test
    void aggregateByFilter_shouldReturnGroupedTotals_withCorrectCountsAndAmounts() {
        var accountId = new AccountId(UUID.randomUUID());
        var now = Instant.now();

        var salary  = sampleTransactionAt(accountId, TransactionType.CREDIT, "3500.00", TransactionCategory.SALARY, now);
        var food1   = sampleTransactionAt(accountId, TransactionType.DEBIT,   "50.00", TransactionCategory.FOOD_AND_DINING, now);
        var food2   = sampleTransactionAt(accountId, TransactionType.DEBIT,   "30.00", TransactionCategory.FOOD_AND_DINING, now);

        // Scope by accountId so transactions saved by other test methods for the same
        // CUSTOMER_ID (e.g. save_shouldPersistTransaction) don't pollute this aggregate.
        var filter = TransactionFilter.builder()
                .customerId(CUSTOMER_ID)
                .accountId(accountId)
                .from(now.minus(1, ChronoUnit.HOURS))
                .to(now.plus(1, ChronoUnit.HOURS))
                .build();

        StepVerifier.create(
                saveTransactionPort.save(salary)
                        .then(saveTransactionPort.save(food1))
                        .then(saveTransactionPort.save(food2))
                        .thenMany(loadTransactionPort.aggregateByFilter(filter))
        )
                .recordWith(ArrayList::new)
                .thenConsumeWhile(a -> true)
                .consumeRecordedWith(aggregates -> {
                    var salaryAgg = aggregates.stream()
                            .filter(a -> a.category() == TransactionCategory.SALARY
                                      && a.type()     == TransactionType.CREDIT)
                            .findFirst().orElseThrow(() -> new AssertionError("SALARY aggregate missing"));
                    assertThat(salaryAgg.totalAmount().amount()).isEqualByComparingTo("3500.00");
                    assertThat(salaryAgg.count()).isEqualTo(1);

                    var foodAgg = aggregates.stream()
                            .filter(a -> a.category() == TransactionCategory.FOOD_AND_DINING
                                      && a.type()     == TransactionType.DEBIT)
                            .findFirst().orElseThrow(() -> new AssertionError("FOOD aggregate missing"));
                    assertThat(foodAgg.totalAmount().amount()).isEqualByComparingTo("80.00");
                    assertThat(foodAgg.count()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void aggregateByFilter_shouldReturnEmpty_whenNoTransactionsMatchFilter() {
        var filter = TransactionFilter.builder()
                .customerId(CUSTOMER_ID)
                .from(Instant.parse("2000-01-01T00:00:00Z"))
                .to(Instant.parse("2000-01-02T00:00:00Z"))
                .build();

        StepVerifier.create(loadTransactionPort.aggregateByFilter(filter))
                .verifyComplete();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Transaction sampleTransaction(TransactionType type, String amount, TransactionCategory category) {
        return sampleTransactionAt(ACCOUNT_ID, type, amount, category, Instant.now());
    }

    private Transaction sampleTransactionAt(AccountId accountId, TransactionType type,
                                             String amount, TransactionCategory category,
                                             Instant occurredAt) {
        return new Transaction(
                TransactionId.generate(),
                CUSTOMER_ID,
                accountId,
                Money.of(new BigDecimal(amount), "GBP"),
                type,
                TransactionStatus.COMPLETED,
                "Test transaction",
                category,
                null,
                DataSourceId.MANUAL,
                occurredAt
        );
    }
}
