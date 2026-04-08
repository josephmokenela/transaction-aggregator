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
 * Simulates a credit/debit card provider: generates food, shopping, and subscription transactions.
 */
@Component
class MockCardDataSourceAdapter implements FetchTransactionsPort {

    private static final DataSourceId SOURCE_ID = DataSourceId.MOCK_CARD;

    private static final List<String[]> DINING_MERCHANTS = List.of(
            new String[]{"Lunch at Cafe Nero", "Cafe Nero"},
            new String[]{"Dinner at Pizza Express", "Pizza Express"},
            new String[]{"McDonald's", "McDonald's"},
            new String[]{"Uber Eats order", "Uber Eats"},
            new String[]{"Sushi restaurant", "Sushi Garden"}
    );

    @Override
    public DataSourceId sourceId() {
        return SOURCE_ID;
    }

    @Override
    public Flux<Transaction> fetchTransactions(CustomerId customerId, Instant from, Instant to) {
        return Flux.fromIterable(generate(customerId, from, to));
    }

    private List<Transaction> generate(CustomerId customerId, Instant from, Instant to) {
        var accountId = deterministicAccount(customerId, "card");
        var transactions = new ArrayList<Transaction>();

        LocalDate cursor = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = to.atZone(ZoneOffset.UTC).toLocalDate();

        int week = 0;
        while (!cursor.isAfter(endDate)) {
            // Dining — every Monday, Wednesday, Friday
            if (cursor.getDayOfWeek() == DayOfWeek.MONDAY
                    || cursor.getDayOfWeek() == DayOfWeek.WEDNESDAY
                    || cursor.getDayOfWeek() == DayOfWeek.FRIDAY) {
                var merchant = DINING_MERCHANTS.get(week % DINING_MERCHANTS.size());
                var amount = new BigDecimal("12.50").add(BigDecimal.valueOf(week % 20));
                var occurredAt = cursor.atTime(12, 30).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    transactions.add(build(customerId, accountId,
                            "dining-" + cursor, occurredAt,
                            amount, TransactionType.DEBIT, merchant[0], merchant[1]));
                }
            }

            // Weekly grocery shop — every Saturday
            if (cursor.getDayOfWeek() == DayOfWeek.SATURDAY) {
                var occurredAt = cursor.atTime(11, 0).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    transactions.add(build(customerId, accountId,
                            "grocery-" + cursor, occurredAt,
                            new BigDecimal("75.00"), TransactionType.DEBIT,
                            "Weekly grocery shop", "Tesco"));
                }
                week++;
            }

            // Monthly subscriptions on the 1st
            if (cursor.getDayOfMonth() == 1) {
                var netflix = cursor.atTime(8, 0).toInstant(ZoneOffset.UTC);
                if (inRange(netflix, from, to)) {
                    transactions.add(build(customerId, accountId, "netflix-" + YearMonth.from(cursor),
                            netflix, new BigDecimal("15.99"), TransactionType.DEBIT, "Netflix subscription", "Netflix"));
                }

                var spotify = cursor.atTime(8, 5).toInstant(ZoneOffset.UTC);
                if (inRange(spotify, from, to)) {
                    transactions.add(build(customerId, accountId, "spotify-" + YearMonth.from(cursor),
                            spotify, new BigDecimal("9.99"), TransactionType.DEBIT, "Spotify Premium", "Spotify"));
                }
            }

            // Mid-month shopping — 15th
            if (cursor.getDayOfMonth() == 15) {
                var occurredAt = cursor.atTime(14, 0).toInstant(ZoneOffset.UTC);
                if (inRange(occurredAt, from, to)) {
                    transactions.add(build(customerId, accountId, "shopping-" + YearMonth.from(cursor),
                            occurredAt, new BigDecimal("89.99"), TransactionType.DEBIT,
                            "Online shopping", "Amazon"));
                }
            }

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
