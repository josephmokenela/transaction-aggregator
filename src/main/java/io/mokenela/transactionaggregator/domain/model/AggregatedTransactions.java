package io.mokenela.transactionaggregator.domain.model;

import java.util.List;

public record AggregatedTransactions(
        AccountId accountId,
        List<TransactionSummary> summaries,
        List<Transaction> transactions
) {}
