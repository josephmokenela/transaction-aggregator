package io.mokenela.transactionaggregator.adapter.out.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link TransactionEntity}.
 *
 * <p>Simple queries are declared as derived method names; the account-period query uses
 * an explicit {@code @Query} to keep the method name readable.</p>
 */
interface R2dbcTransactionRepository extends ReactiveCrudRepository<TransactionEntity, UUID> {

    @Query("""
            SELECT * FROM transactions
            WHERE account_id = :accountId
            ORDER BY occurred_at DESC
            LIMIT :limit
            """)
    Flux<TransactionEntity> findByAccountId(
            @Param("accountId") UUID accountId,
            @Param("limit") int limit);

    @Query("""
            SELECT * FROM transactions
            WHERE account_id = :accountId
              AND occurred_at >= :from
              AND occurred_at <= :to
            ORDER BY occurred_at
            LIMIT :limit
            """)
    Flux<TransactionEntity> findByAccountIdAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit);

    @Query("""
            INSERT INTO transactions (
                id, customer_id, account_id, amount, currency_code, type, status,
                description, category, merchant_name, data_source_id, occurred_at
            ) VALUES (
                :id, :customerId, :accountId, :amount, :currencyCode,
                :type::transaction_type, :status::transaction_status,
                :description, :category::transaction_category, :merchantName, :dataSourceId, :occurredAt
            )
            ON CONFLICT (id) DO UPDATE SET
                status         = EXCLUDED.status,
                amount         = EXCLUDED.amount,
                category       = EXCLUDED.category,
                merchant_name  = EXCLUDED.merchant_name,
                occurred_at    = EXCLUDED.occurred_at
            RETURNING *
            """)
    Mono<TransactionEntity> upsert(
            @Param("id")           UUID id,
            @Param("customerId")   UUID customerId,
            @Param("accountId")    UUID accountId,
            @Param("amount")       java.math.BigDecimal amount,
            @Param("currencyCode") String currencyCode,
            @Param("type")         String type,
            @Param("status")       String status,
            @Param("description")  String description,
            @Param("category")     String category,
            @Param("merchantName") String merchantName,
            @Param("dataSourceId") String dataSourceId,
            @Param("occurredAt")   Instant occurredAt);
}
