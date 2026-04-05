package io.mokenela.transactionaggregator.domain.port.out;

import io.mokenela.transactionaggregator.domain.model.Transaction;
import reactor.core.publisher.Mono;

public interface SaveTransactionPort {
    Mono<Transaction> save(Transaction transaction);
}
