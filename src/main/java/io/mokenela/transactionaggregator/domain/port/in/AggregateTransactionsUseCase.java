package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.AggregatedTransactions;
import reactor.core.publisher.Mono;

public interface AggregateTransactionsUseCase {
    Mono<AggregatedTransactions> aggregate(AggregateTransactionsQuery query);
}
