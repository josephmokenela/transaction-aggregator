package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CustomerSummaryResponse(
        String customerId,
        String customerName,
        Instant from,
        Instant to,
        BigDecimal totalInflow,
        BigDecimal totalOutflow,
        BigDecimal netPosition,
        String currency,
        long totalTransactions,
        List<CategorySummaryResponse> categoryBreakdown
) {

    static CustomerSummaryResponse from(CustomerSummary summary) {
        return new CustomerSummaryResponse(
                summary.customerId().value().toString(),
                summary.customerName(),
                summary.from(),
                summary.to(),
                summary.totalInflow().amount(),
                summary.totalOutflow().amount(),
                summary.netPosition().amount(),
                summary.totalInflow().currency().getCurrencyCode(),
                summary.totalTransactions(),
                summary.categoryBreakdown().stream().map(CategorySummaryResponse::from).toList()
        );
    }
}
