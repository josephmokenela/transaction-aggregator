package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.Customer;

public record CustomerResponse(
        String id,
        String name,
        String email
) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id().value().toString(),
                customer.name(),
                customer.email()
        );
    }
}
