package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.Customer;
import reactor.core.publisher.Flux;

public interface ListCustomersUseCase {
    Flux<Customer> listCustomers();
}
