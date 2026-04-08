package io.mokenela.transactionaggregator.adapter.out.datasource;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.FetchTransactionsPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simulates a payment provider (e.g. PayPal/Revolut): generates transport, P2P, and healthcare transactions.
 */
@Component
class MockPaymentProviderDataSourceAdapter implements FetchTransactionsPort {

    private static final DataSourceId SOURCE_ID = DataSourceId.MOCK_PAYMENT_PROVIDER;

    @Override
    public DataSourceId sourceId() {
        return SOURCE_ID;
    }

    @Override
    public Flux<Transaction> fetchTransactions(CustomerId customerId, Instant from, Instant to) {
        return Flux.fromIterable(generate(customerId, from, to));
    }

    private List<Transaction> generate(CustomerId customerId, Instant from, Instant to) {
        var accountId = deterministicAccount(customerId, "pp");
        var transactions = new ArrayList<Transaction>();

        LocalDate cursor = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = to.atZone(ZoneOffset.UTC).toLocalDate();

        int day = 0;
        while (!cursor.isAfter(endDate)) {

            // Uber rides — every Tuesday and Thursday
            if (cursor.getDayOfWeek() == DayOfWeek.TUESDAY || cursor.getDayOfWeek() == DayOfWeek.THURSDAY) {
                var amount = new BigDecimal("8.50").add(BigDecimal.valueOf(day % 10));
                var occurredAt = cursor.atTime(8, 15).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    transactions.add(build(customerId, accountId, "uber-" + cursor, occurredAt,
                            amount, TransactionType.DEBIT, "Uber ride", "Uber"));
                }
            }

            // Monthly pharmacy — 12th
            if (cursor.getDayOfMonth() == 12) {
                var occurredAt = cursor.atTime(10, 30).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    transactions.add(build(customerId, accountId,
                            "pharmacy-" + YearMonth.from(cursor), occurredAt,
                            new BigDecimal("22.50"), TransactionType.DEBIT,
                            "Pharmacy prescription", "Boots Pharmacy"));
                }
            }

            // Bi-weekly P2P transfer — 7th and 21st
            if (cursor.getDayOfMonth() == 7 || cursor.getDayOfMonth() == 21) {
                var occurredAt = cursor.atTime(18, 0).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    String label = cursor.getDayOfMonth() == 7 ? "p2p-a" : "p2p-b";
                    transactions.add(build(customerId, accountId,
                            label + "-" + YearMonth.from(cursor), occurredAt,
                            new BigDecimal("50.00"), TransactionType.DEBIT,
                            "P2P Transfer to friend", null));
                }
            }

            // Monthly insurance — 20th
            if (cursor.getDayOfMonth() == 20) {
                var occurredAt = cursor.atTime(9, 0).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    transactions.add(build(customerId, accountId,
                            "insurance-" + YearMonth.from(cursor), occurredAt,
                            new BigDecimal("45.00"), TransactionType.DEBIT,
                            "Car insurance payment", "Aviva Insurance"));
                }
            }

            // Monthly train/commute pass — 28th
            if (cursor.getDayOfMonth() == 28) {
                var occurredAt = cursor.atTime(7, 30).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    transactions.add(build(customerId, accountId,
                            "rail-" + YearMonth.from(cursor), occurredAt,
                            new BigDecimal("150.00"), TransactionType.DEBIT,
                            "Monthly rail pass", "National Rail"));
                }
            }

            day++;
            cursor = cursor.plusDays(1);
        }

        return transactions;
    }

    private Transaction build(CustomerId customerId, AccountId accountId, String key,
                               Instant occurredAt, BigDecimal amount, TransactionType type,
                               String description, String merchantName) {
        return new Transaction(
                new TransactionId(deterministicUUID(customerId, key)),
                customerId,
                accountId,
                Money.of(amount, "GBP"),
                type,
                TransactionStatus.COMPLETED,
                description,
                TransactionCategory.OTHER,
                merchantName,
                SOURCE_ID,
                occurredAt
        );
    }

    private AccountId deterministicAccount(CustomerId customerId, String suffix) {
        return new AccountId(deterministicUUID(customerId, suffix));
    }

    private UUID deterministicUUID(CustomerId customerId, String key) {
        return UUID.nameUUIDFromBytes(
                (customerId.value().toString() + "-" + SOURCE_ID.value() + "-" + key)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private boolean inRange(Instant date, Instant from, Instant to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
