package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.port.in.PageRequest;
import io.mokenela.transactionaggregator.domain.port.in.PagedResponse;
import io.mokenela.transactionaggregator.domain.port.out.LoadCustomerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * R2DBC-backed implementation of {@link LoadCustomerPort}.
 */
@Component
class CustomerPersistenceAdapter implements LoadCustomerPort {

    private final R2dbcCustomerRepository repository;
    private final DatabaseClient databaseClient;
    private final CustomerEntityMapper mapper;
    private final CircuitBreaker circuitBreaker;

    CustomerPersistenceAdapter(R2dbcCustomerRepository repository,
                               DatabaseClient databaseClient,
                               CustomerEntityMapper mapper,
                               CircuitBreaker databaseCircuitBreaker) {
        this.repository = repository;
        this.databaseClient = databaseClient;
        this.mapper = mapper;
        this.circuitBreaker = databaseCircuitBreaker;
    }

    @Override
    public Mono<Customer> loadById(CustomerId customerId) {
        return repository.findById(customerId.value())
                .map(mapper::toDomain)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    @Override
    public Mono<PagedResponse<Customer>> loadAll(PageRequest pageRequest) {
        Mono<java.util.List<Customer>> contentMono = databaseClient
                .sql("SELECT id, name, email FROM customers ORDER BY name ASC LIMIT :limit OFFSET :offset")
                .bind("limit", pageRequest.size())
                .bind("offset", pageRequest.offset())
                .map((row, meta) -> {
                    var entity = new CustomerEntity();
                    entity.setId(row.get("id", java.util.UUID.class));
                    entity.setName(row.get("name", String.class));
                    entity.setEmail(row.get("email", String.class));
                    return mapper.toDomain(entity);
                })
                .all()
                .collectList();

        Mono<Long> countMono = databaseClient
                .sql("SELECT COUNT(*) FROM customers")
                .map((row, meta) -> row.get(0, Long.class))
                .one();

        return contentMono.zipWith(countMono)
                .map(tuple -> {
                    long total = tuple.getT2();
                    long totalPages = (long) Math.ceil((double) total / pageRequest.size());
                    return new PagedResponse<>(tuple.getT1(), pageRequest.page(), pageRequest.size(),
                            total, totalPages);
                })
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
