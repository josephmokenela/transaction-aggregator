package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.TransactionFilter;

public record SearchTransactionsQuery(
        TransactionFilter filter,
        int limit
) {

    public SearchTransactionsQuery {
        if (filter == null) throw new IllegalArgumentException("filter cannot be null");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    }

    public static SearchTransactionsQuery of(TransactionFilter filter) {
        return new SearchTransactionsQuery(filter, 50);
    }
}
