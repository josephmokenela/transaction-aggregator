package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.Transaction;
import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import io.mokenela.transactionaggregator.domain.model.TransactionStatus;
import io.mokenela.transactionaggregator.domain.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String id,
        String customerId,
        String accountId,
        BigDecimal amount,
        String currency,
        TransactionType type,
        TransactionStatus status,
        String description,
        TransactionCategory category,
        String merchantName,
        String dataSourceId,
        Instant occurredAt
) {

    static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.id().value().toString(),
                t.customerId().value().toString(),
                t.accountId().value().toString(),
                t.amount().amount(),
                t.amount().currency().getCurrencyCode(),
                t.type(),
                t.status(),
                t.description(),
                t.category(),
                t.merchantName(),
                t.dataSourceId().value(),
                t.occurredAt()
        );
    }
}
