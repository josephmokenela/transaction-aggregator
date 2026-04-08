package io.mokenela.transactionaggregator.domain.model;

import java.math.BigDecimal;

public record CategorySummary(
        TransactionCategory category,
        Money totalAmount,
        long transactionCount,
        BigDecimal percentage
) {}
