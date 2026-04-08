package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.ListAvailableSourcesUseCase;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsCommand;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsUseCase;
import io.mokenela.transactionaggregator.domain.port.out.FetchTransactionsPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class DataSyncService implements SyncTransactionsUseCase, ListAvailableSourcesUseCase {

    private static final Logger log = LoggerFactory.getLogger(DataSyncService.class);

    private final List<FetchTransactionsPort> sources;
    private final SaveTransactionPort saveTransactionPort;
    private final TransactionCategorizationService categorizationService;

    public DataSyncService(List<FetchTransactionsPort> sources,
                           SaveTransactionPort saveTransactionPort,
                           TransactionCategorizationService categorizationService) {
        this.sources = sources;
        this.saveTransactionPort = saveTransactionPort;
        this.categorizationService = categorizationService;
        log.info("DataSyncService initialised with {} data source(s): {}", sources.size(),
                sources.stream().map(s -> s.sourceId().value()).toList());
    }

    @Override
    public List<DataSourceId> listAvailableSources() {
        return sources.stream().map(FetchTransactionsPort::sourceId).toList();
    }

    @Override
    public Mono<SyncResult> sync(SyncTransactionsCommand command) {
        log.info("Starting sync for customer={} from={} to={}",
                command.customerId().value(), command.from(), command.to());

        return Flux.fromIterable(sources)
                .flatMap(source -> syncFromSource(source, command.customerId(), command.from(), command.to())
                        .map(count -> new SourceSyncCount(source.sourceId(), count)))
                .collectList()
                .map(counts -> {
                    int total = counts.stream().mapToInt(SourceSyncCount::count).sum();
                    log.info("Sync complete for customer={}: {} transaction(s) across {} source(s)",
                            command.customerId().value(), total, counts.size());
                    return new SyncResult(
                            command.customerId(),
                            counts.stream().map(SourceSyncCount::sourceId).toList(),
                            total,
                            Instant.now()
                    );
                });
    }

    private Mono<Integer> syncFromSource(FetchTransactionsPort source, CustomerId customerId,
                                         Instant from, Instant to) {
        return source.fetchTransactions(customerId, from, to)
                .map(this::applyCategory)
                .flatMap(saveTransactionPort::save)
                .doOnNext(t -> log.trace("Saved transaction id={} source={} category={}",
                        t.id().value(), t.dataSourceId().value(), t.category()))
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.debug("Synced {} transaction(s) from source={}",
                        count, source.sourceId().value()))
                .doOnError(ex -> log.error("Failed to sync from source={}: {}",
                        source.sourceId().value(), ex.getMessage(), ex));
    }

    private Transaction applyCategory(Transaction transaction) {
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
