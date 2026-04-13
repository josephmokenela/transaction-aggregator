package io.mokenela.transactionaggregator.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j circuit breaker configuration for outbound database calls.
 *
 * <p>A single {@code "database"} circuit breaker is shared across all persistence
 * adapters. When the database is unavailable or consistently slow, the breaker opens
 * and fast-fails subsequent calls for {@code waitDurationInOpenState} before probing
 * again in half-open state. This prevents thread/connection pool exhaustion and gives
 * the database time to recover.</p>
 *
 * <p>Thresholds are tuned for a low-traffic service with a small connection pool.
 * Raise {@code slidingWindowSize} and lower {@code failureRateThreshold} for
 * higher-throughput deployments.</p>
 */
@Configuration
class ResilienceConfig {

    @Bean
    CircuitBreaker databaseCircuitBreaker() {
        var config = CircuitBreakerConfig.custom()
                // Evaluate failure rate over the last 10 calls (count-based window).
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                // Open the breaker when 50% of calls in the window fail.
                .failureRateThreshold(50)
                // Stay open for 30 seconds before transitioning to half-open.
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // Allow 3 probe calls in half-open state before deciding to close or reopen.
                .permittedNumberOfCallsInHalfOpenState(3)
                // Automatically move from open → half-open after the wait duration,
                // without requiring an incoming call to trigger the transition.
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Treat any exception as a failure (R2DBC errors, timeouts, etc.).
                .recordExceptions(Exception.class)
                .build();

        return CircuitBreaker.of("database", config);
    }
}
