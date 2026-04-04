package io.mokenela.transactionaggregator.domain.model;

import java.util.UUID;

public record AccountId(UUID value) {

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }
}
