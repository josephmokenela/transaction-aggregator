package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.AggregatedTransactions;

import java.util.List;

public record AggregatedTransactionsResponse(
        String accountId,
        List<TransactionSummaryResponse> summaries,
        List<TransactionResponse> transactions
) {

    static AggregatedTransactionsResponse from(AggregatedTransactions aggregated) {
        return new AggregatedTransactionsResponse(
                aggregated.accountId().value().toString(),
                aggregated.summaries().stream().map(TransactionSummaryResponse::from).toList(),
                aggregated.transactions().stream().map(TransactionResponse::from).toList()
        );
    }
}
