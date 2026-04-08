package io.mokenela.transactionaggregator.domain.port.out;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.DataSourceId;
import io.mokenela.transactionaggregator.domain.model.Transaction;
import reactor.core.publisher.Flux;

import java.time.Instant;

/**
 * Output port implemented by each external data source adapter (bank, card provider, etc.).
 */
public interface FetchTransactionsPort {
    DataSourceId sourceId();
    Flux<Transaction> fetchTransactions(CustomerId customerId, Instant from, Instant to);
}
