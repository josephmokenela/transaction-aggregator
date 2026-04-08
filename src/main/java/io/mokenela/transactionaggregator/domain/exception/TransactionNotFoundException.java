package io.mokenela.transactionaggregator.domain.exception;

import io.mokenela.transactionaggregator.domain.model.TransactionId;

public final class TransactionNotFoundException extends RuntimeException {

    private final TransactionId transactionId;

    public TransactionNotFoundException(TransactionId transactionId) {
        super("Transaction not found: " + transactionId.value());
        this.transactionId = transactionId;
    }

    public TransactionId transactionId() {
        return transactionId;
    }
}
