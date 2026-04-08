package io.mokenela.transactionaggregator.domain.model;

import java.time.Instant;
import java.util.List;

public record CustomerSummary(
        CustomerId customerId,
        String customerName,
        Instant from,
        Instant to,
        Money totalInflow,
        Money totalOutflow,
        Money netPosition,
        long totalTransactions,
        List<CategorySummary> categoryBreakdown
) {}
