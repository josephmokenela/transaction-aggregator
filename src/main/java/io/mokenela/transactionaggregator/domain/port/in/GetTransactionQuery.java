package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.TransactionId;

public record GetTransactionQuery(TransactionId transactionId) {

    public GetTransactionQuery {
        if (transactionId == null) throw new IllegalArgumentException("transactionId cannot be null");
    }
}
