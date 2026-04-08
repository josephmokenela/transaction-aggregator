package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.port.out.LoadCustomerPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
class CustomerPersistenceAdapter implements LoadCustomerPort {

    private final InMemoryCustomerRepository repository;

    CustomerPersistenceAdapter(InMemoryCustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Customer> loadById(CustomerId customerId) {
        return Mono.fromCallable(() -> repository.findById(customerId))
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Flux<Customer> loadAll() {
        return Flux.fromIterable(repository.findAll());
    }
}
