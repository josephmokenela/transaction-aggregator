package io.mokenela.transactionaggregator.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Custom health indicator for Kafka broker connectivity.
 * Exposed via /actuator/health as "kafka" — enables readiness probes to detect
 * Kafka outages before they silently affect event consumption.
 */
@Component("kafkaHealthIndicator")
class KafkaHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    private final String bootstrapServers;

    KafkaHealthIndicator(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(this::checkKafka)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("Kafka health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down().withDetail("error", ex.getMessage()).build());
                });
    }

    private Health checkKafka() {
        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000",
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "3000"
        );
        try (var adminClient = AdminClient.create(config)) {
            var nodes = adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS);
            return Health.up()
                    .withDetail("brokerCount", nodes.size())
                    .withDetail("bootstrapServers", bootstrapServers)
                    .build();
        } catch (Exception ex) {
            log.warn("Kafka health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
