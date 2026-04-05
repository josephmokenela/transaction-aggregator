package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.AggregationPeriod;

import java.time.Instant;

public record AggregateTransactionsQuery(
        AccountId accountId,
        AggregationPeriod period,
        Instant from,
        Instant to
) {

    public AggregateTransactionsQuery {
        if (accountId == null) throw new IllegalArgumentException("accountId cannot be null");
        if (period == null) throw new IllegalArgumentException("period cannot be null");
        if (from == null) throw new IllegalArgumentException("from cannot be null");
        if (to == null) throw new IllegalArgumentException("to cannot be null");
        if (!from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
    }
}
