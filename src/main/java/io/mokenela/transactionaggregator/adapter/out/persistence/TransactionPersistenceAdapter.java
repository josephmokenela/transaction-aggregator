package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * R2DBC-backed implementation of the persistence output ports.
 *
 * <p>Simple lookups delegate to {@link R2dbcTransactionRepository}; dynamic filtering
 * uses {@link R2dbcEntityTemplate} with the Criteria API so filter predicates are composed
 * at runtime without string interpolation.</p>
 *
 * <p>Because all operations use the non-blocking R2DBC driver, no explicit scheduler
 * switch is required — the reactive pipeline runs on Netty's event-loop threads.</p>
 */
@Component
class TransactionPersistenceAdapter implements SaveTransactionPort, LoadTransactionPort {

    private final R2dbcTransactionRepository repository;
    private final R2dbcEntityTemplate template;
    private final TransactionEntityMapper mapper;

    TransactionPersistenceAdapter(R2dbcTransactionRepository repository,
                                  R2dbcEntityTemplate template,
                                  TransactionEntityMapper mapper) {
        this.repository = repository;
        this.template = template;
        this.mapper = mapper;
    }

    @Override
    public Mono<Transaction> save(Transaction transaction) {
        var entity = mapper.toEntity(transaction);
        // upsert: insert new or overwrite on PK conflict (idempotent sync re-runs)
        return template.insert(entity)
                .onErrorResume(DataIntegrityViolationException.class, ex ->
                        template.update(entity)
                                .switchIfEmpty(Mono.error(new IllegalStateException(
                                        "Upsert failed: transaction " + entity.getId() + " could not be inserted or updated"))))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Transaction> loadById(TransactionId transactionId) {
        return repository.findById(transactionId.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<Transaction> loadByAccountId(AccountId accountId) {
        return repository.findByAccountId(accountId.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<Transaction> loadByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to) {
        return repository.findByAccountIdAndPeriod(accountId.value(), from, to).map(mapper::toDomain);
    }

    @Override
    public Flux<Transaction> loadByFilter(TransactionFilter filter) {
        return template.select(Query.query(buildCriteria(filter)), TransactionEntity.class)
                .map(mapper::toDomain);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Builds a Criteria chain from the non-null filter fields.
     * All conditions are ANDed; the keyword condition is an OR sub-expression
     * that matches description OR merchant_name.
     */
    private Criteria buildCriteria(TransactionFilter filter) {
        var criteria = Criteria.empty();

        if (filter.customerId() != null)
            criteria = criteria.and(Criteria.where("customer_id").is(filter.customerId().value()));
        if (filter.accountId() != null)
            criteria = criteria.and(Criteria.where("account_id").is(filter.accountId().value()));
        if (filter.category() != null)
            criteria = criteria.and(Criteria.where("category").is(filter.category().name()));
        if (filter.type() != null)
            criteria = criteria.and(Criteria.where("type").is(filter.type().name()));
        if (filter.dataSourceId() != null)
            criteria = criteria.and(Criteria.where("data_source_id").is(filter.dataSourceId().value()));
        if (filter.from() != null)
            criteria = criteria.and(Criteria.where("occurred_at").greaterThanOrEquals(filter.from()));
        if (filter.to() != null)
            criteria = criteria.and(Criteria.where("occurred_at").lessThanOrEquals(filter.to()));
        if (filter.keyword() != null) {
            var kw = "%" + filter.keyword() + "%";
            criteria = criteria.and(
                    Criteria.where("description").like(kw)
                            .or("merchant_name").like(kw));
        }

        return criteria;
    }
}
