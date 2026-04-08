package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.port.out.LoadCustomerPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC-backed implementation of {@link LoadCustomerPort}.
 */
@Component
class CustomerPersistenceAdapter implements LoadCustomerPort {

    private final R2dbcCustomerRepository repository;
    private final CustomerEntityMapper mapper;

    CustomerPersistenceAdapter(R2dbcCustomerRepository repository, CustomerEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Customer> loadById(CustomerId customerId) {
        return repository.findById(customerId.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<Customer> loadAll() {
        return repository.findAll().map(mapper::toDomain);
    }
}
