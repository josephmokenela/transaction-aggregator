package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsCommand;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsUseCase;
import io.mokenela.transactionaggregator.domain.port.out.FetchTransactionsPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class DataSyncService implements SyncTransactionsUseCase {

    private final List<FetchTransactionsPort> sources;
    private final SaveTransactionPort saveTransactionPort;
    private final TransactionCategorizationService categorizationService;

    public DataSyncService(List<FetchTransactionsPort> sources,
                           SaveTransactionPort saveTransactionPort,
                           TransactionCategorizationService categorizationService) {
        this.sources = sources;
        this.saveTransactionPort = saveTransactionPort;
        this.categorizationService = categorizationService;
    }

    @Override
    public Mono<SyncResult> sync(SyncTransactionsCommand command) {
        return Flux.fromIterable(sources)
                .flatMap(source -> syncFromSource(source, command.customerId(), command.from(), command.to())
                        .map(count -> new SourceSyncCount(source.sourceId(), count)))
                .collectList()
                .map(counts -> new SyncResult(
                        command.customerId(),
                        counts.stream().map(SourceSyncCount::sourceId).toList(),
                        counts.stream().mapToInt(SourceSyncCount::count).sum(),
                        Instant.now()
                ));
    }

    private Mono<Integer> syncFromSource(FetchTransactionsPort source, CustomerId customerId,
                                         Instant from, Instant to) {
        return source.fetchTransactions(customerId, from, to)
                .map(this::categorize)
                .flatMap(saveTransactionPort::save)
                .count()
                .map(Long::intValue);
    }

    private Transaction categorize(Transaction transaction) {
        var category = categorizationService.categorize(transaction.description(), transaction.merchantName());
        return new Transaction(
                transaction.id(),
                transaction.customerId(),
                transaction.accountId(),
                transaction.amount(),
                transaction.type(),
                transaction.status(),
                transaction.description(),
                category,
                transaction.merchantName(),
                transaction.dataSourceId(),
                transaction.occurredAt()
        );
    }

    private record SourceSyncCount(DataSourceId sourceId, int count) {}
}
