package io.mokenela.transactionaggregator.adapter.out.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

interface R2dbcAccountRepository extends ReactiveCrudRepository<AccountEntity, UUID> {

    /**
     * Upserts an account — creates it if it doesn't exist, does nothing if it does.
     * Called during transaction ingestion to ensure the FK constraint on
     * transactions.account_id is satisfied before the transaction is saved.
     */
    @Query("""
            INSERT INTO accounts (id, customer_id, name, source, created_at)
            VALUES (:id, :customerId, :name, :source, now())
            ON CONFLICT (id) DO NOTHING
            """)
    Mono<Void> upsert(
            @Param("id")         UUID id,
            @Param("customerId") UUID customerId,
            @Param("name")       String name,
            @Param("source")     String source);
}
