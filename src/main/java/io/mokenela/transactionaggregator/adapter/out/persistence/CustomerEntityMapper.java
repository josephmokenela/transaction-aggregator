package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import org.springframework.stereotype.Component;

/**
 * Bidirectional mapper between the {@link Customer} domain record and {@link CustomerEntity}.
 */
@Component
class CustomerEntityMapper {

    Customer toDomain(CustomerEntity entity) {
        return new Customer(new CustomerId(entity.getId()), entity.getName(), entity.getEmail());
    }

    CustomerEntity toEntity(Customer domain) {
        var entity = new CustomerEntity();
        entity.setId(domain.id().value());
        entity.setName(domain.name());
        entity.setEmail(domain.email());
        return entity;
    }
}
