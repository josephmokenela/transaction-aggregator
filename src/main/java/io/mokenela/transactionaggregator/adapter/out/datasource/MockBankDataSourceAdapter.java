package io.mokenela.transactionaggregator.adapter.out.datasource;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.FetchTransactionsPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simulates a retail bank: generates salary credits, monthly bills, and rent payments.
 */
@Component
class MockBankDataSourceAdapter implements FetchTransactionsPort {

    private static final DataSourceId SOURCE_ID = DataSourceId.MOCK_BANK;

    @Override
    public DataSourceId sourceId() {
        return SOURCE_ID;
    }

    @Override
    public Flux<Transaction> fetchTransactions(CustomerId customerId, Instant from, Instant to) {
        return Flux.fromIterable(generate(customerId, from, to));
    }

    private List<Transaction> generate(CustomerId customerId, Instant from, Instant to) {
        var accountId = deterministicAccount(customerId, "bank");
        var transactions = new ArrayList<Transaction>();

        YearMonth start = YearMonth.from(from.atZone(ZoneOffset.UTC));
        YearMonth end = YearMonth.from(to.atZone(ZoneOffset.UTC));

        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            // Salary — 1st of each month
            var salaryDate = month.atDay(1).atTime(8, 0).toInstant(ZoneOffset.UTC);
            if (inRange(salaryDate, from, to)) {
                transactions.add(build(customerId, accountId, "salary-" + month, salaryDate,
                        new BigDecimal("3500.00"), TransactionType.CREDIT,
                        "Monthly Salary", "Employer Ltd"));
            }

            // Rent — 2nd of each month
            var rentDate = month.atDay(2).atTime(9, 0).toInstant(ZoneOffset.UTC);
            if (inRange(rentDate, from, to)) {
                transactions.add(build(customerId, accountId, "rent-" + month, rentDate,
                        new BigDecimal("1200.00"), TransactionType.DEBIT,
                        "Monthly Rent Payment", "Lettings Agency"));
            }

            // Electricity — 5th
            var electricDate = month.atDay(5).atTime(10, 0).toInstant(ZoneOffset.UTC);
            if (inRange(electricDate, from, to)) {
                transactions.add(build(customerId, accountId, "electricity-" + month, electricDate,
                        new BigDecimal("85.00"), TransactionType.DEBIT,
                        "Electricity Bill", "EnergySupplier"));
            }

            // Gas — 6th
            var gasDate = month.atDay(6).atTime(10, 0).toInstant(ZoneOffset.UTC);
            if (inRange(gasDate, from, to)) {
                transactions.add(build(customerId, accountId, "gas-" + month, gasDate,
                        new BigDecimal("55.00"), TransactionType.DEBIT,
                        "Gas Bill", "British Gas"));
            }

            // Internet — 8th
            var internetDate = month.atDay(8).atTime(10, 0).toInstant(ZoneOffset.UTC);
            if (inRange(internetDate, from, to)) {
                transactions.add(build(customerId, accountId, "internet-" + month, internetDate,
                        new BigDecimal("40.00"), TransactionType.DEBIT,
                        "Broadband Bill", "Virgin Media"));
            }

            // Phone — 10th
            var phoneDate = month.atDay(10).atTime(10, 0).toInstant(ZoneOffset.UTC);
            if (inRange(phoneDate, from, to)) {
                transactions.add(build(customerId, accountId, "phone-" + month, phoneDate,
                        new BigDecimal("30.00"), TransactionType.DEBIT,
                        "Mobile Phone Bill", "BT "));
            }

            // Bank transfer out — 15th
            var transferDate = month.atDay(15).atTime(14, 0).toInstant(ZoneOffset.UTC);
            if (inRange(transferDate, from, to)) {
                transactions.add(build(customerId, accountId, "transfer-" + month, transferDate,
                        new BigDecimal("200.00"), TransactionType.DEBIT,
                        "Transfer to Savings", null));
            }
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
                TransactionCategory.OTHER,  // will be categorized by DataSyncService
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
