package io.mokenela.transactionaggregator.adapter.out.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data R2DBC entity — a flat, mutable projection of the {@code transactions} table.
 *
 * <p>Deliberately kept inside the {@code persistence} package: it is an infrastructure detail
 * and is never exposed to the domain layer. Conversion to and from the domain model is handled
 * exclusively by {@link TransactionEntityMapper}.</p>
 *
 * <p>Field names use camelCase; Spring Data R2DBC converts them to snake_case column names
 * automatically (e.g. {@code customerId} → {@code customer_id}).</p>
 */
@Table("transactions")
@Data
@NoArgsConstructor
class TransactionEntity {

    @Id
    private UUID id;
    private UUID customerId;
    private UUID accountId;
    private BigDecimal amount;
    private String currencyCode;
    private String type;
    private String status;
    private String description;
    private String category;
    private String merchantName;
    private String dataSourceId;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
