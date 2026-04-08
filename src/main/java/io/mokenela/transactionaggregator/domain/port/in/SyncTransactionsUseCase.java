package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.SyncResult;
import reactor.core.publisher.Mono;

public interface SyncTransactionsUseCase {
    Mono<SyncResult> sync(SyncTransactionsCommand command);
}
