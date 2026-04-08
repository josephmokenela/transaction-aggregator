package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
                .filter(t -> inPeriod(t.occurredAt(), from, to));
    }

    Stream<Transaction> findByFilter(TransactionFilter filter) {
        return store.values().stream()
                .filter(t -> filter.customerId() == null || t.customerId().equals(filter.customerId()))
                .filter(t -> filter.accountId() == null || t.accountId().equals(filter.accountId()))
                .filter(t -> filter.category() == null || t.category() == filter.category())
                .filter(t -> filter.type() == null || t.type() == filter.type())
                .filter(t -> filter.dataSourceId() == null || t.dataSourceId().equals(filter.dataSourceId()))
                .filter(t -> filter.from() == null || !t.occurredAt().isBefore(filter.from()))
                .filter(t -> filter.to() == null || !t.occurredAt().isAfter(filter.to()))
                .filter(t -> filter.keyword() == null || matchesKeyword(t, filter.keyword()));
    }

    private boolean inPeriod(Instant occurredAt, Instant from, Instant to) {
        return !occurredAt.isBefore(from) && !occurredAt.isAfter(to);
    }

    private boolean matchesKeyword(Transaction t, String keyword) {
        var lower = keyword.toLowerCase();
        return (t.description() != null && t.description().toLowerCase().contains(lower))
                || (t.merchantName() != null && t.merchantName().toLowerCase().contains(lower));
    }
}
