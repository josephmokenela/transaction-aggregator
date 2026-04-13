package io.mokenela.transactionaggregator.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Custom health indicator for the R2DBC PostgreSQL connection pool.
 * Supplements Spring Boot's default R2DBC health check with an explicit ping
 * so readiness probes detect pool exhaustion and connection failures early.
 */
@Component("databaseHealthIndicator")
class DatabaseHealthIndicator implements ReactiveHealthIndicator {

    private final DatabaseClient databaseClient;

    DatabaseHealthIndicator(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Health> health() {
        return databaseClient.sql("SELECT 1")
                .map((row, meta) -> row.get(0, Integer.class))
                .one()
                .map(result -> Health.up().withDetail("ping", result).build())
                .onErrorResume(ex -> Mono.just(
                        Health.down().withDetail("error", ex.getMessage()).build()
                ));
    }
}
