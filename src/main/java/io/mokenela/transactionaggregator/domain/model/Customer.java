package io.mokenela.transactionaggregator.domain.model;

public record Customer(
        CustomerId id,
        String name,
        String email
) {

    public Customer {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email cannot be blank");
    }
}
