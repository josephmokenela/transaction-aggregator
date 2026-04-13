package io.mokenela.transactionaggregator.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CustomerTest {

    private static final CustomerId VALID_ID    = new CustomerId(UUID.randomUUID());
    private static final String     VALID_NAME  = "Alice Smith";
    private static final String     VALID_EMAIL = "alice@example.com";

    @Test
    void constructor_shouldCreateCustomer_withValidInputs() {
        var customer = new Customer(VALID_ID, VALID_NAME, VALID_EMAIL);

        assertThat(customer.id()).isEqualTo(VALID_ID);
        assertThat(customer.name()).isEqualTo(VALID_NAME);
        assertThat(customer.email()).isEqualTo(VALID_EMAIL);
    }

    @Test
    void constructor_shouldThrow_whenIdIsNull() {
        assertThatThrownBy(() -> new Customer(null, VALID_NAME, VALID_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void constructor_shouldThrow_whenNameIsBlankOrNull(String name) {
        assertThatThrownBy(() -> new Customer(VALID_ID, name, VALID_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void constructor_shouldThrow_whenEmailIsBlankOrNull(String email) {
        assertThatThrownBy(() -> new Customer(VALID_ID, VALID_NAME, email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void customerId_of_shouldCreateFromUuidString() {
        String uuidStr = UUID.randomUUID().toString();
        var id = CustomerId.of(uuidStr);
        assertThat(id.value().toString()).isEqualTo(uuidStr);
    }
}
