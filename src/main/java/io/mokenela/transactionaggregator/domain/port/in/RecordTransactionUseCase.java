package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.Transaction;
import reactor.core.publisher.Mono;

public interface RecordTransactionUseCase {
    Mono<Transaction> recordTransaction(RecordTransactionCommand command);
}
