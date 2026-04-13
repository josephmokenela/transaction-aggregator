package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.Customer;
import reactor.core.publisher.Mono;

public interface ListCustomersUseCase {
    Mono<PagedResponse<Customer>> listCustomers(PageRequest pageRequest);
}
