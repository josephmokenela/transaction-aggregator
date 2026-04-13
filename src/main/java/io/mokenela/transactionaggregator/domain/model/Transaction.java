package io.mokenela.transactionaggregator.domain.model;

import io.mokenela.transactionaggregator.util.Mask;

import java.time.Instant;

public record Transaction(
        TransactionId id,
        CustomerId customerId,
        AccountId accountId,
        Money amount,
        TransactionType type,
        TransactionStatus status,
        String description,
        TransactionCategory category,
        String merchantName,      // nullable — not all transactions have a merchant
        DataSourceId dataSourceId,
        Instant occurredAt
) {

    public Transaction {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        if (customerId == null) throw new IllegalArgumentException("customerId cannot be null");
        if (accountId == null) throw new IllegalArgumentException("accountId cannot be null");
        if (amount == null) throw new IllegalArgumentException("amount cannot be null");
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
        if (category == null) throw new IllegalArgumentException("category cannot be null");
        if (dataSourceId == null) throw new IllegalArgumentException("dataSourceId cannot be null");
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

    /** Masks financial and free-text fields so accidental logging never exposes PII. */
    @Override
    public String toString() {
        return "Transaction[id=" + id +
               ", customerId=" + customerId +
               ", accountId=" + accountId +
               ", amount=" + Mask.amount(amount.amount()) +
               ", type=" + type +
               ", status=" + status +
               ", description=" + Mask.text(description) +
               ", category=" + category +
               ", merchantName=" + Mask.text(merchantName) +
               ", dataSourceId=" + dataSourceId +
               ", occurredAt=" + occurredAt + "]";
    }
}
