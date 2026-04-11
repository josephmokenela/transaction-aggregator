package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.micrometer.core.instrument.MeterRegistry;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
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
    private final R2dbcAccountRepository accountRepository;
    private final R2dbcEntityTemplate template;
    private final TransactionEntityMapper mapper;
    private final MeterRegistry meterRegistry;
    private final R2dbcConverter converter;

    TransactionPersistenceAdapter(R2dbcTransactionRepository repository,
                                  R2dbcAccountRepository accountRepository,
                                  R2dbcEntityTemplate template,
                                  TransactionEntityMapper mapper,
                                  MeterRegistry meterRegistry,
                                  R2dbcConverter converter) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.template = template;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
        this.converter = converter;
    }

    @Override
    public Mono<Transaction> save(Transaction transaction) {
        var e = mapper.toEntity(transaction);
        var timer = io.micrometer.core.instrument.Timer.builder("transactions.save.duration")
                .tag("source", transaction.dataSourceId().value())
                .publishPercentileHistogram()
                .register(meterRegistry);
        var sample = io.micrometer.core.instrument.Timer.start(meterRegistry);

        // Ensure the account exists before inserting the transaction (FK constraint)
        return accountRepository.upsert(
                e.getAccountId(),
                e.getCustomerId(),
                null,
                transaction.dataSourceId().value()
        ).then(
            repository.upsert(
                    e.getId(), e.getCustomerId(), e.getAccountId(),
                    e.getAmount(), e.getCurrencyCode(), e.getType(), e.getStatus(),
                    e.getDescription(), e.getCategory(), e.getMerchantName(),
                    e.getDataSourceId(), e.getOccurredAt()
            )
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
        return repository.findByAccountId(accountId.value(), MAX_LIMIT).map(mapper::toDomain);
    }

    @Override
    public Flux<Transaction> loadByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to) {
        return repository.findByAccountIdAndPeriod(accountId.value(), from, to, MAX_PERIOD_LIMIT)
                .map(mapper::toDomain);
    }

    private static final int MAX_LIMIT        = 1000;
    private static final int MAX_PERIOD_LIMIT = 5_000;

    @Override
    public Flux<Transaction> loadByFilter(TransactionFilter filter, int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);

        // Keyword search uses the GIN full-text index — route to a dynamic SQL query
        // that also applies any other active filter dimensions (customer, account, dates).
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            return searchByKeywordWithFilters(filter, effectiveLimit);
        }

        return template.select(
                Query.query(buildCriteria(filter)).limit(effectiveLimit),
                TransactionEntity.class
        ).map(mapper::toDomain);
    }

    /**
     * Full-text keyword search that also honours all other active filter fields.
     * Uses DatabaseClient with named parameters to build the WHERE clause dynamically
     * so no filter dimension is silently dropped when keyword is present.
     */
    private Flux<Transaction> searchByKeywordWithFilters(TransactionFilter filter, int limit) {
        var sql = new StringBuilder("""
                SELECT * FROM transactions
                WHERE to_tsvector('english', coalesce(description, '') || ' ' || coalesce(merchant_name, ''))
                      @@ plainto_tsquery('english', :keyword)
                """);

        if (filter.customerId()  != null) sql.append(" AND customer_id    = :customerId");
        if (filter.accountId()   != null) sql.append(" AND account_id     = :accountId");
        if (filter.category()    != null) sql.append(" AND category       = :category");
        if (filter.type()        != null) sql.append(" AND type           = :type");
        if (filter.dataSourceId()!= null) sql.append(" AND data_source_id = :dataSourceId");
        if (filter.from()        != null) sql.append(" AND occurred_at   >= :from");
        if (filter.to()          != null) sql.append(" AND occurred_at   <= :to");
        sql.append(" ORDER BY occurred_at DESC LIMIT :limit");

        var spec = template.getDatabaseClient()
                .sql(sql.toString())
                .bind("keyword", filter.keyword())
                .bind("limit", limit);

        if (filter.customerId()   != null) spec = spec.bind("customerId",   filter.customerId().value());
        if (filter.accountId()    != null) spec = spec.bind("accountId",    filter.accountId().value());
        if (filter.category()     != null) spec = spec.bind("category",     filter.category().name());
        if (filter.type()         != null) spec = spec.bind("type",         filter.type().name());
        if (filter.dataSourceId() != null) spec = spec.bind("dataSourceId", filter.dataSourceId().value());
        if (filter.from()         != null) spec = spec.bind("from",         filter.from());
        if (filter.to()           != null) spec = spec.bind("to",           filter.to());

        return spec.map((row, meta) -> converter.read(TransactionEntity.class, row))
                .all()
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
        return criteria;
    }
}
