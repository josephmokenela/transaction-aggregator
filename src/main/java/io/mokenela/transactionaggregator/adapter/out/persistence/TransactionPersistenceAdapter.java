package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

/**
 * In-memory implementation of the persistence ports.
 *
 * <p>All operations are wrapped with {@link Schedulers#boundedElastic()} to prevent
 * ConcurrentHashMap iteration from occupying the Netty event-loop threads.
 * A production implementation would instead use a non-blocking driver (R2DBC, MongoDB reactive, etc.)
 * and would not need an explicit scheduler.</p>
 */
@Component
class TransactionPersistenceAdapter implements SaveTransactionPort, LoadTransactionPort {

    private final InMemoryTransactionRepository repository;

    TransactionPersistenceAdapter(InMemoryTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Transaction> save(Transaction transaction) {
        return Mono.fromCallable(() -> repository.save(transaction))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Transaction> loadById(TransactionId transactionId) {
        return Mono.fromCallable(() -> repository.findById(transactionId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Flux<Transaction> loadByAccountId(AccountId accountId) {
        return Flux.fromStream(() -> repository.findByAccountId(accountId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Transaction> loadByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to) {
        return Flux.fromStream(() -> repository.findByAccountIdAndPeriod(accountId, from, to))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Transaction> loadByFilter(TransactionFilter filter) {
        return Flux.fromStream(() -> repository.findByFilter(filter))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
