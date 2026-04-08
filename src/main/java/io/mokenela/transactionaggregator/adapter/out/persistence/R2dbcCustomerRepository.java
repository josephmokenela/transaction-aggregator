package io.mokenela.transactionaggregator.adapter.out.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link CustomerEntity}.
 * All required operations ({@code findById}, {@code findAll}) are inherited from
 * {@link ReactiveCrudRepository}.
 */
interface R2dbcCustomerRepository extends ReactiveCrudRepository<CustomerEntity, UUID> {
}
