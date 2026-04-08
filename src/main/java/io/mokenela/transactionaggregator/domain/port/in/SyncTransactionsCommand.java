package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.CustomerId;

import java.time.Instant;

public record SyncTransactionsCommand(
        CustomerId customerId,
        Instant from,
        Instant to
) {

    public SyncTransactionsCommand {
        if (customerId == null) throw new IllegalArgumentException("customerId cannot be null");
        if (from == null) throw new IllegalArgumentException("from cannot be null");
        if (to == null) throw new IllegalArgumentException("to cannot be null");
        if (!from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
    }
}
