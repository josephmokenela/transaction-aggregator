package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.AbstractIntegrationTest;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
