package io.mokenela.transactionaggregator.domain.model;

import java.util.UUID;

public record CustomerId(UUID value) {

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }
}
