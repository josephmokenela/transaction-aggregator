package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.Customer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerEntityMapperTest {

    private final CustomerEntityMapper mapper = new CustomerEntityMapper();

    private static final UUID ID    = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String NAME  = "Alice Smith";
    private static final String EMAIL = "alice@example.com";

    @Test
    void toDomain_shouldMapAllFieldsCorrectly() {
        var entity = new CustomerEntity();
        entity.setId(ID);
        entity.setName(NAME);
        entity.setEmail(EMAIL);

        Customer domain = mapper.toDomain(entity);

        assertThat(domain.id().value()).isEqualTo(ID);
        assertThat(domain.name()).isEqualTo(NAME);
        assertThat(domain.email()).isEqualTo(EMAIL);
    }

    @Test
    void toEntity_shouldMapAllFieldsCorrectly() {
        var domain = new Customer(new CustomerId(ID), NAME, EMAIL);

        CustomerEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getName()).isEqualTo(NAME);
        assertThat(entity.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void toDomain_toEntity_shouldBeSymmetric() {
        var domain = new Customer(new CustomerId(ID), NAME, EMAIL);
        var entity = mapper.toEntity(domain);
        var roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.id()).isEqualTo(domain.id());
        assertThat(roundTripped.name()).isEqualTo(domain.name());
        assertThat(roundTripped.email()).isEqualTo(domain.email());
    }
}
