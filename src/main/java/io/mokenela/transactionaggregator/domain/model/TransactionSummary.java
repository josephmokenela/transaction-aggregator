package io.mokenela.transactionaggregator.domain.model;

import java.time.Instant;

public record TransactionSummary(
        AccountId accountId,
        AggregationPeriod period,
        Instant periodStart,
        Instant periodEnd,
        Money totalCredits,
        Money totalDebits,
        Money netAmount,
        long transactionCount
) {}
