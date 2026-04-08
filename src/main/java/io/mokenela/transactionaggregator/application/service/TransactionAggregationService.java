package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        implements RecordTransactionUseCase, AggregateTransactionsUseCase, GetTransactionUseCase, SearchTransactionsUseCase {

    private final SaveTransactionPort saveTransactionPort;
    private final LoadTransactionPort loadTransactionPort;
    private final TransactionCategorizationService categorizationService;

    public TransactionAggregationService(SaveTransactionPort saveTransactionPort,
                                         LoadTransactionPort loadTransactionPort,
                                         TransactionCategorizationService categorizationService) {
        this.saveTransactionPort = saveTransactionPort;
        this.loadTransactionPort = loadTransactionPort;
        this.categorizationService = categorizationService;
    }

    @Override
    public Mono<Transaction> recordTransaction(RecordTransactionCommand command) {
        var category = categorizationService.categorize(command.description(), command.merchantName());
        var transaction = new Transaction(
                TransactionId.generate(),
                command.customerId(),
                command.accountId(),
                command.amount(),
                command.type(),
                TransactionStatus.COMPLETED,
                command.description(),
                category,
                command.merchantName(),
                DataSourceId.MANUAL,
                Instant.now()
        );
        return saveTransactionPort.save(transaction);
    }

    @Override
    public Mono<Transaction> getTransaction(GetTransactionQuery query) {
        return loadTransactionPort.loadById(query.transactionId());
    }

    @Override
    public Flux<Transaction> search(SearchTransactionsQuery query) {
        return loadTransactionPort
                .loadByFilter(query.filter())
                .take(query.limit());
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
        return new AggregatedTransactions(query.accountId(), buildSummaries(query, transactions), transactions);
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
                .map(e -> buildSummary(query.accountId(), query.period(), e.getKey(), e.getValue(), currencyCode))
                .sorted(Comparator.comparing(TransactionSummary::periodStart))
                .toList();
    }

    private Instant truncateToPeriod(Instant instant, AggregationPeriod period, ZoneId zone) {
        ZonedDateTime zdt = instant.atZone(zone);
        return switch (period) {
            case HOURLY -> zdt.truncatedTo(ChronoUnit.HOURS).toInstant();
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

        return new TransactionSummary(
                accountId,
                period,
                periodStart,
                calculatePeriodEnd(periodStart, period),
                totalCredits,
                totalDebits,
                totalCredits.subtract(totalDebits),
                transactions.size()
        );
    }

    private Instant calculatePeriodEnd(Instant periodStart, AggregationPeriod period) {
        ZonedDateTime zdt = periodStart.atZone(ZoneId.systemDefault());
        return switch (period) {
            case HOURLY -> zdt.plusHours(1).minusNanos(1).toInstant();
            case DAILY -> zdt.plusDays(1).minusNanos(1).toInstant();
            case WEEKLY -> zdt.plusWeeks(1).minusNanos(1).toInstant();
            case MONTHLY -> zdt.plusMonths(1).minusNanos(1).toInstant();
            case YEARLY -> zdt.plusYears(1).minusNanos(1).toInstant();
        };
    }
}
