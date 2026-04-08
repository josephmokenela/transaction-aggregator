package io.mokenela.transactionaggregator.domain.port.out;

import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LoadCustomerPort {
    Mono<Customer> loadById(CustomerId customerId);
    Flux<Customer> loadAll();
}
