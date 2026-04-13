package io.mokenela.transactionaggregator.domain.port.out;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.CategoryAggregate;
import io.mokenela.transactionaggregator.domain.model.Transaction;
import io.mokenela.transactionaggregator.domain.model.TransactionFilter;
import io.mokenela.transactionaggregator.domain.model.TransactionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface LoadTransactionPort {
    Mono<Transaction> loadById(TransactionId transactionId);
    Flux<Transaction> loadByAccountId(AccountId accountId);
    Flux<Transaction> loadByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to);
    Flux<Transaction> loadByFilter(TransactionFilter filter, int limit);

    /**
     * Returns pre-aggregated totals grouped by (type, category) using a SQL {@code GROUP BY}
     * query. Callers receive at most {@code TransactionType.values() × TransactionCategory.values()}
     * rows regardless of how many individual transactions exist — no JVM-level row limit applies.
     */
    Flux<CategoryAggregate> aggregateByFilter(TransactionFilter filter);
}
