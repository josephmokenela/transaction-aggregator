package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
class InMemoryCustomerRepository {

    private final Map<CustomerId, Customer> store = new ConcurrentHashMap<>();

    InMemoryCustomerRepository() {
        seed();
    }

    Optional<Customer> findById(CustomerId id) {
        return Optional.ofNullable(store.get(id));
    }

    Collection<Customer> findAll() {
        return store.values();
    }

    private void seed() {
        var customers = java.util.List.of(
                new Customer(new CustomerId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                        "Alice Johnson", "alice.johnson@example.com"),
                new Customer(new CustomerId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                        "Bob Smith", "bob.smith@example.com"),
                new Customer(new CustomerId(UUID.fromString("33333333-3333-3333-3333-333333333333")),
                        "Carol Williams", "carol.williams@example.com")
        );
        customers.forEach(c -> store.put(c.id(), c));
    }
}
