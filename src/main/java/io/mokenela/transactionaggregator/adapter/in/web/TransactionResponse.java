package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.Transaction;
import io.mokenela.transactionaggregator.domain.model.TransactionStatus;
import io.mokenela.transactionaggregator.domain.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        TransactionType type,
        TransactionStatus status,
        String description,
        Instant occurredAt
) {

    static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id().value().toString(),
                transaction.accountId().value().toString(),
                transaction.amount().amount(),
                transaction.amount().currency().getCurrencyCode(),
                transaction.type(),
                transaction.status(),
                transaction.description(),
                transaction.occurredAt()
        );
    }
}
