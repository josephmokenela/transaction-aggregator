package io.mokenela.transactionaggregator.adapter.out.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link TransactionEntity}.
 *
 * <p>Simple queries are declared as derived method names; the account-period query uses
 * an explicit {@code @Query} to keep the method name readable.</p>
 */
interface R2dbcTransactionRepository extends ReactiveCrudRepository<TransactionEntity, UUID> {

    Flux<TransactionEntity> findByAccountId(UUID accountId);

    @Query("""
            SELECT * FROM transactions
            WHERE account_id = :accountId
              AND occurred_at >= :from
              AND occurred_at <= :to
            ORDER BY occurred_at
            """)
    Flux<TransactionEntity> findByAccountIdAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
