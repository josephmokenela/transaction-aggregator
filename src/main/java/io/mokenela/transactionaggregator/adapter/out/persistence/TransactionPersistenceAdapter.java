package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

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
    private final CircuitBreaker circuitBreaker;

    TransactionPersistenceAdapter(R2dbcTransactionRepository repository,
                                  R2dbcAccountRepository accountRepository,
                                  R2dbcEntityTemplate template,
                                  TransactionEntityMapper mapper,
                                  MeterRegistry meterRegistry,
                                  R2dbcConverter converter,
                                  CircuitBreaker databaseCircuitBreaker) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.template = template;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
        this.converter = converter;
        this.circuitBreaker = databaseCircuitBreaker;
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
        .map(mapper::toDomain)
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    @Override
    public Mono<Transaction> loadById(TransactionId transactionId) {
        return repository.findById(transactionId.value())
                .map(mapper::toDomain)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    @Override
    public Flux<Transaction> loadByAccountId(AccountId accountId) {
        return repository.findByAccountId(accountId.value(), MAX_LIMIT)
                .map(mapper::toDomain)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    @Override
    public Flux<Transaction> loadByAccountIdAndPeriod(AccountId accountId, Instant from, Instant to) {
        return repository.findByAccountIdAndPeriod(accountId.value(), from, to, MAX_PERIOD_LIMIT)
                .map(mapper::toDomain)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    private static final int MAX_LIMIT        = 1000;
    private static final int MAX_PERIOD_LIMIT = 5_000;

    @Override
    public Flux<Transaction> loadByFilter(TransactionFilter filter, int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);

        // Route to DatabaseClient whenever enum-typed columns (category, type) or a full-text
        // keyword are in the filter. The Criteria API sends String values with an explicit
        // PostgreSQL text OID; the driver then refuses the implicit cast to transaction_category
        // or transaction_type enum columns. DatabaseClient binds without an OID ("unknown"),
        // which PostgreSQL will implicitly cast to the target enum type.
        boolean needsDatabaseClient = filter.category() != null
                || filter.type() != null
                || (filter.keyword() != null && !filter.keyword().isBlank());

        if (needsDatabaseClient) {
            return queryWithDatabaseClient(filter, effectiveLimit);
        }

        return template.select(
                Query.query(buildCriteria(filter)).limit(effectiveLimit),
                TransactionEntity.class
        ).map(mapper::toDomain)
         .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /**
     * Builds and executes a dynamic SQL query via DatabaseClient using PostgreSQL
     * positional parameters ({@code $1}, {@code $2}, ...).
     *
     * <p>Named parameters (e.g. {@code :name}) are NOT used here because the PostgreSQL
     * R2DBC driver requires positional markers and the Spring R2DBC named-parameter
     * expansion layer is not reliable for all code paths. Positional binding via
     * {@link org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec#bind(int, Object)}
     * is the safest approach.</p>
     *
     * <p>Enum-typed columns ({@code category}, {@code type}) receive an explicit PostgreSQL
     * type cast ({@code $n::transaction_category}, {@code $n::transaction_type}) because the
     * driver sends String parameters with an {@code unknown} OID and PostgreSQL still needs
     * an explicit hint to resolve the target enum type in some prepared-statement contexts.</p>
     */
    private Flux<Transaction> queryWithDatabaseClient(TransactionFilter filter, int limit) {
        var sql     = new StringBuilder("SELECT * FROM transactions WHERE 1=1");
        var params  = new ArrayList<>();

        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            params.add(filter.keyword());
            sql.append("""

                AND to_tsvector('english', coalesce(description, '') || ' ' || coalesce(merchant_name, ''))
                    @@ plainto_tsquery('english', $""").append(params.size()).append(")");
        }
        if (filter.customerId()   != null) { params.add(filter.customerId().value());    sql.append(" AND customer_id    = $").append(params.size()); }
        if (filter.accountId()    != null) { params.add(filter.accountId().value());     sql.append(" AND account_id     = $").append(params.size()); }
        if (filter.category()     != null) { params.add(filter.category().name());       sql.append(" AND category       = $").append(params.size()).append("::transaction_category"); }
        if (filter.type()         != null) { params.add(filter.type().name());           sql.append(" AND type           = $").append(params.size()).append("::transaction_type"); }
        if (filter.dataSourceId() != null) { params.add(filter.dataSourceId().value());  sql.append(" AND data_source_id = $").append(params.size()); }
        if (filter.from()         != null) { params.add(filter.from());                  sql.append(" AND occurred_at   >= $").append(params.size()); }
        if (filter.to()           != null) { params.add(filter.to());                    sql.append(" AND occurred_at   <= $").append(params.size()); }
        params.add(limit);
        sql.append(" ORDER BY occurred_at DESC LIMIT $").append(params.size());

        var spec = template.getDatabaseClient().sql(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            spec = spec.bind(i, params.get(i));
        }

        return spec.map((row, meta) -> converter.read(TransactionEntity.class, row))
                .all()
                .map(mapper::toDomain)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /**
     * Runs a SQL {@code GROUP BY (type, category, currency_code)} query so that summary
     * calculations never load individual transaction rows into the JVM. The result set is
     * bounded by the product of the two enum cardinalities (≤ ~40 rows) regardless of how
     * many transactions the customer has.
     */
    @Override
    public Flux<CategoryAggregate> aggregateByFilter(TransactionFilter filter) {
        var sql    = new StringBuilder(
                "SELECT type, category, currency_code, SUM(amount) AS total_amount, COUNT(*) AS tx_count\n" +
                "FROM transactions WHERE 1=1");
        var params = new ArrayList<>();

        if (filter.customerId()   != null) { params.add(filter.customerId().value());   sql.append(" AND customer_id    = $").append(params.size()); }
        if (filter.accountId()    != null) { params.add(filter.accountId().value());    sql.append(" AND account_id     = $").append(params.size()); }
        if (filter.category()     != null) { params.add(filter.category().name());      sql.append(" AND category       = $").append(params.size()).append("::transaction_category"); }
        if (filter.type()         != null) { params.add(filter.type().name());          sql.append(" AND type           = $").append(params.size()).append("::transaction_type"); }
        if (filter.dataSourceId() != null) { params.add(filter.dataSourceId().value()); sql.append(" AND data_source_id = $").append(params.size()); }
        if (filter.from()         != null) { params.add(filter.from());                 sql.append(" AND occurred_at   >= $").append(params.size()); }
        if (filter.to()           != null) { params.add(filter.to());                   sql.append(" AND occurred_at   <= $").append(params.size()); }
        sql.append(" GROUP BY type, category, currency_code ORDER BY total_amount DESC");

        var spec = template.getDatabaseClient().sql(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            spec = spec.bind(i, params.get(i));
        }

        return spec.map((row, meta) -> {
            var type         = TransactionType.valueOf(row.get("type", String.class));
            var category     = TransactionCategory.valueOf(row.get("category", String.class));
            var currencyCode = row.get("currency_code", String.class);
            var totalAmount  = Money.of(row.get("total_amount", BigDecimal.class), currencyCode);
            var count        = row.get("tx_count", Long.class);
            return new CategoryAggregate(type, category, totalAmount, count);
        }).all()
          .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
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
