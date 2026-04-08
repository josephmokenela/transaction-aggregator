package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.Transaction;
import reactor.core.publisher.Flux;

public interface SearchTransactionsUseCase {
    Flux<Transaction> search(SearchTransactionsQuery query);
}
