package io.mokenela.transactionaggregator.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data R2DBC entity for the {@code accounts} table.
 *
 * <p>Accounts are created on first sight during transaction ingestion — they are
 * not independently managed by this service, but their existence is required for
 * referential integrity on the {@code transactions.account_id} FK.</p>
 */
@Table("accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
class AccountEntity {

    @Id
    private UUID id;
    private UUID customerId;
    private String name;
    private String source;
    private Instant createdAt;
}
