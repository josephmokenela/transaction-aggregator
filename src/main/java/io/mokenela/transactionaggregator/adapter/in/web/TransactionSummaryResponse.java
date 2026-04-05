package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.AggregationPeriod;
import io.mokenela.transactionaggregator.domain.model.TransactionSummary;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionSummaryResponse(
        String accountId,
        AggregationPeriod period,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal netAmount,
        String currency,
        long transactionCount
) {

    static TransactionSummaryResponse from(TransactionSummary summary) {
        return new TransactionSummaryResponse(
                summary.accountId().value().toString(),
                summary.period(),
                summary.periodStart(),
                summary.periodEnd(),
                summary.totalCredits().amount(),
                summary.totalDebits().amount(),
                summary.netAmount().amount(),
                summary.totalCredits().currency().getCurrencyCode(),
                summary.transactionCount()
        );
    }
}
