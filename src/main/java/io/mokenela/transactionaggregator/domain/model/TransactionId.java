package io.mokenela.transactionaggregator.domain.model;

import java.util.UUID;

public record TransactionId(UUID value) {

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(String value) {
        return new TransactionId(UUID.fromString(value));
    }
}
