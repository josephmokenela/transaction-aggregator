package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.AggregationPeriod;
import io.mokenela.transactionaggregator.domain.model.CustomerId;

import java.time.Instant;
import java.util.Objects;

public record AggregateTransactionsQuery(
        AccountId accountId,
        AggregationPeriod period,
        Instant from,
        Instant to,
        CustomerId customerId  // null for admins (unscoped); set for customers to enforce ownership
) {

    public AggregateTransactionsQuery {
        if (Objects.isNull(accountId)){
            throw new IllegalArgumentException("accountId cannot be null");
        }
        if (Objects.isNull(period)) {
            throw new IllegalArgumentException("period cannot be null");
        }
        if (Objects.isNull(from)) {
            throw new IllegalArgumentException("from cannot be null");
        }
        if (Objects.isNull(to)) {
            throw new IllegalArgumentException("to cannot be null");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }
}
