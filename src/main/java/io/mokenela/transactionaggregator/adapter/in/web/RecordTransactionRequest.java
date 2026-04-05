package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.TransactionType;

import java.math.BigDecimal;

public record RecordTransactionRequest(
        String accountId,
        BigDecimal amount,
        String currency,
        TransactionType type,
        String description
) {}
