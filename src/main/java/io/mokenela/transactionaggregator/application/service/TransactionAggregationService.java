package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TransactionAggregationService
        implements RecordTransactionUseCase, AggregateTransactionsUseCase, GetTransactionUseCase {

    private final SaveTransactionPort saveTransactionPort;
    private final LoadTransactionPort loadTransactionPort;

    public TransactionAggregationService(SaveTransactionPort saveTransactionPort,
                                         LoadTransactionPort loadTransactionPort) {
        this.saveTransactionPort = saveTransactionPort;
        this.loadTransactionPort = loadTransactionPort;
    }

    @Override
    public Mono<Transaction> recordTransaction(RecordTransactionCommand command) {
        var transaction = new Transaction(
                TransactionId.generate(),
                command.accountId(),
                command.amount(),
                command.type(),
                TransactionStatus.COMPLETED,
                command.description(),
                Instant.now()
        );
        return saveTransactionPort.save(transaction);
    }

    @Override
    public Mono<Transaction> getTransaction(GetTransactionQuery query) {
        return loadTransactionPort.loadById(query.transactionId());
    }

    @Override
    public Mono<AggregatedTransactions> aggregate(AggregateTransactionsQuery query) {
        return loadTransactionPort
                .loadByAccountIdAndPeriod(query.accountId(), query.from(), query.to())
                .collectList()
                .map(transactions -> buildAggregatedTransactions(query, transactions));
    }

    private AggregatedTransactions buildAggregatedTransactions(AggregateTransactionsQuery query,
                                                                List<Transaction> transactions) {
        var summaries = buildSummaries(query, transactions);
        return new AggregatedTransactions(query.accountId(), summaries, transactions);
    }

    private List<TransactionSummary> buildSummaries(AggregateTransactionsQuery query,
                                                     List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }

        var currencyCode = transactions.getFirst().amount().currency().getCurrencyCode();
        var zone = ZoneId.systemDefault();

        Map<Instant, List<Transaction>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(t -> truncateToPeriod(t.occurredAt(), query.period(), zone)));

        return grouped.entrySet().stream()
                .map(entry -> buildSummary(query.accountId(), query.period(), entry.getKey(), entry.getValue(), currencyCode))
                .sorted(Comparator.comparing(TransactionSummary::periodStart))
                .toList();
    }

    private Instant truncateToPeriod(Instant instant, AggregationPeriod period, ZoneId zone) {
        ZonedDateTime zdt = instant.atZone(zone);
        return switch (period) {
            case DAILY -> zdt.truncatedTo(ChronoUnit.DAYS).toInstant();
            case WEEKLY -> zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                             .truncatedTo(ChronoUnit.DAYS).toInstant();
            case MONTHLY -> zdt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
            case YEARLY -> zdt.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        };
    }

    private TransactionSummary buildSummary(AccountId accountId, AggregationPeriod period,
                                            Instant periodStart, List<Transaction> transactions,
                                            String currencyCode) {
        var zero = Money.zero(currencyCode);

        var totalCredits = transactions.stream()
                .filter(Transaction::isCredit)
                .map(Transaction::amount)
                .reduce(zero, Money::add);

        var totalDebits = transactions.stream()
                .filter(Transaction::isDebit)
                .map(Transaction::amount)
                .reduce(zero, Money::add);

        var netAmount = totalCredits.subtract(totalDebits);
        var periodEnd = calculatePeriodEnd(periodStart, period);

        return new TransactionSummary(
                accountId,
                period,
                periodStart,
                periodEnd,
                totalCredits,
                totalDebits,
                netAmount,
                transactions.size()
        );
    }

    private Instant calculatePeriodEnd(Instant periodStart, AggregationPeriod period) {
        ZonedDateTime zdt = periodStart.atZone(ZoneId.systemDefault());
        return switch (period) {
            case DAILY -> zdt.plusDays(1).minusNanos(1).toInstant();
            case WEEKLY -> zdt.plusWeeks(1).minusNanos(1).toInstant();
            case MONTHLY -> zdt.plusMonths(1).minusNanos(1).toInstant();
            case YEARLY -> zdt.plusYears(1).minusNanos(1).toInstant();
        };
    }
}
