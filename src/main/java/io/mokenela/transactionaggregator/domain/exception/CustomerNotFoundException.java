package io.mokenela.transactionaggregator.domain.exception;

import io.mokenela.transactionaggregator.domain.model.CustomerId;

public final class CustomerNotFoundException extends RuntimeException {

    private final CustomerId customerId;

    public CustomerNotFoundException(CustomerId customerId) {
        super("Customer not found: " + customerId.value());
        this.customerId = customerId;
    }

    public CustomerId customerId() {
        return customerId;
    }
}
