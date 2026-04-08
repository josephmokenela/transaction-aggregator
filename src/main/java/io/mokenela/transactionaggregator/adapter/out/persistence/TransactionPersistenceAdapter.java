package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
class TransactionPersistenceAdapter implements SaveTransactionPort, LoadTransactionPort {

    private final InMemoryTransactionRepository repository;

    TransactionPersistenceAdapter(InMemoryTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Transaction> save(Transaction transaction) {
        return Mono.fromCallable(() -> repository.save(transaction));
    }

    @Override
    public Mono<Transaction> loadById(TransactionId transactionId) {
        return Mono.fromCallable(() -> repository.findById(transactionId))
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Flux<Transaction> loadByAccountId(AccountId accountId) {
        return Flux.fromStream(() -> repository.findByAccountId(accountId));
    }

    @Override
    public Flux<Transaction> loadByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to) {
        return Flux.fromStream(() -> repository.findByAccountIdAndPeriod(accountId, from, to));
    }

    @Override
    public Flux<Transaction> loadByFilter(TransactionFilter filter) {
        return Flux.fromStream(() -> repository.findByFilter(filter));
    }
}
