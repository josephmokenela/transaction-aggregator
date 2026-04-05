package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.Transaction;
import io.mokenela.transactionaggregator.domain.model.TransactionId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Component
class InMemoryTransactionRepository {

    private final ConcurrentHashMap<TransactionId, Transaction> store = new ConcurrentHashMap<>();

    Transaction save(Transaction transaction) {
        store.put(transaction.id(), transaction);
        return transaction;
    }

    Optional<Transaction> findById(TransactionId id) {
        return Optional.ofNullable(store.get(id));
    }

    Stream<Transaction> findByAccountId(AccountId accountId) {
        return store.values().stream()
                .filter(t -> t.accountId().equals(accountId));
    }

    Stream<Transaction> findByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to) {
        return findByAccountId(accountId)
                .filter(t -> !t.occurredAt().isBefore(from) && !t.occurredAt().isAfter(to));
    }

    Collection<Transaction> findAll() {
        return store.values();
    }
}
