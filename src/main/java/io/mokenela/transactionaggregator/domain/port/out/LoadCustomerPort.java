package io.mokenela.transactionaggregator.domain.port.out;

import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.port.in.PageRequest;
import io.mokenela.transactionaggregator.domain.port.in.PagedResponse;
import reactor.core.publisher.Mono;

public interface LoadCustomerPort {
    Mono<Customer> loadById(CustomerId customerId);
    Mono<PagedResponse<Customer>> loadAll(PageRequest pageRequest);
}
