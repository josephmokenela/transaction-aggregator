package io.mokenela.transactionaggregator.domain.port.out;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.Transaction;
import io.mokenela.transactionaggregator.domain.model.TransactionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface LoadTransactionPort {
    Mono<Transaction> loadById(TransactionId transactionId);
    Flux<Transaction> loadByAccountId(AccountId accountId);
    Flux<Transaction> loadByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to);
}
