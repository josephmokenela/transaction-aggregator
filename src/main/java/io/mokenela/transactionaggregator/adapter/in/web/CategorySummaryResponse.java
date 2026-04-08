package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CategorySummary;
import io.mokenela.transactionaggregator.domain.model.TransactionCategory;

import java.math.BigDecimal;

public record CategorySummaryResponse(
        TransactionCategory category,
        BigDecimal totalAmount,
        String currency,
        long transactionCount,
        BigDecimal percentage
) {

    static CategorySummaryResponse from(CategorySummary summary) {
        return new CategorySummaryResponse(
                summary.category(),
                summary.totalAmount().amount(),
                summary.totalAmount().currency().getCurrencyCode(),
                summary.transactionCount(),
                summary.percentage()
        );
    }
}
