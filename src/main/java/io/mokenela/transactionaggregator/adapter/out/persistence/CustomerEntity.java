package io.mokenela.transactionaggregator.adapter.out.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Spring Data R2DBC entity for the {@code customers} table.
 *
 * <p>Kept package-private — converted to/from domain model via {@link CustomerEntityMapper}.</p>
 */
@Table("customers")
@Data
@NoArgsConstructor
class CustomerEntity {

    @Id
    private UUID id;
    private String name;
    private String email;
}
