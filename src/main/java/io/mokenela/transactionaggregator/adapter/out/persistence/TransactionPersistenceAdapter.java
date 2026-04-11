package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.micrometer.core.instrument.MeterRegistry;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
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
    private final MeterRegistry meterRegistry;

    TransactionPersistenceAdapter(R2dbcTransactionRepository repository,
                                  R2dbcEntityTemplate template,
                                  TransactionEntityMapper mapper,
                                  MeterRegistry meterRegistry) {
        this.repository = repository;
        this.template = template;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Transaction> save(Transaction transaction) {
        var e = mapper.toEntity(transaction);
        var timer = meterRegistry.timer("transactions.save.duration",
                "source", transaction.dataSourceId().value());
        var sample = io.micrometer.core.instrument.Timer.start(meterRegistry);
        return repository.upsert(
                e.getId(), e.getCustomerId(), e.getAccountId(),
                e.getAmount(), e.getCurrencyCode(), e.getType(), e.getStatus(),
                e.getDescription(), e.getCategory(), e.getMerchantName(),
                e.getDataSourceId(), e.getOccurredAt()
        )
        .doOnSuccess(saved -> {
            sample.stop(timer);
            meterRegistry.counter("transactions.saves",
                    "source", transaction.dataSourceId().value()).increment();
        })
        .doOnError(ex -> sample.stop(timer))
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

    private static final int MAX_LIMIT = 1000;

    @Override
    public Flux<Transaction> loadByFilter(TransactionFilter filter, int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        return template.select(
                Query.query(buildCriteria(filter)).limit(effectiveLimit),
                TransactionEntity.class
        ).map(mapper::toDomain);
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
