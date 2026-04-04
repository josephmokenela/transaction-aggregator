package io.mokenela.transactionaggregator.domain.model;

import java.time.Instant;

public record Transaction(
        TransactionId id,
        AccountId accountId,
        Money amount,
        TransactionType type,
        TransactionStatus status,
        String description,
        Instant occurredAt
) {

    public Transaction {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        if (accountId == null) throw new IllegalArgumentException("accountId cannot be null");
        if (amount == null) throw new IllegalArgumentException("amount cannot be null");
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt cannot be null");
    }

    public boolean isCredit() {
        return type == TransactionType.CREDIT;
    }

    public boolean isDebit() {
        return type == TransactionType.DEBIT;
    }

    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }
}
