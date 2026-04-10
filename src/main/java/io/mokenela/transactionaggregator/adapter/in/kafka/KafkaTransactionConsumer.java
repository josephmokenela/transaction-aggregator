package io.mokenela.transactionaggregator.adapter.in.kafka;

import io.mokenela.transactionaggregator.application.service.TransactionCategorizationService;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Consumes transaction events from the Kafka topic and persists them.
 * Batch mode keeps throughput high — Kafka delivers a list of records per poll.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class KafkaTransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionConsumer.class);

    private final SaveTransactionPort saveTransactionPort;
    private final TransactionCategorizationService categorizationService;

    KafkaTransactionConsumer(SaveTransactionPort saveTransactionPort,
                             TransactionCategorizationService categorizationService) {
        this.saveTransactionPort = saveTransactionPort;
        this.categorizationService = categorizationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "transaction-aggregator",
            batch = "true",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(List<KafkaTransactionEvent> events) {
        log.info("Received batch of {} transaction event(s) from Kafka", events.size());

        Flux.fromIterable(events)
                .map(this::toDomain)
                .onErrorContinue((ex, obj) -> log.error("Failed to map event to domain, skipping: {}", ex.getMessage(), ex))
                .flatMap(t -> saveTransactionPort.save(t)
                        .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                                .filter(ex -> ex.getMessage() != null && ex.getMessage().contains("R2DBC Connection"))
                                .doBeforeRetry(sig -> log.warn("Retrying save for id={} (attempt {})", t.id().value(), sig.totalRetriesInARow() + 1)))
                        .doOnError(ex -> log.error("Failed to save transaction id={}: {}", t.id().value(), ex.getMessage()))
                        .onErrorResume(ex -> reactor.core.publisher.Mono.empty()), 8)
                .doOnComplete(() -> log.info("Batch of {} event(s) processed", events.size()))
                .subscribe(
                        t -> {},
                        ex -> log.error("Fatal error in Kafka consumer pipeline", ex)
                );
    }

    private Transaction toDomain(KafkaTransactionEvent event) {
        var category = categorizationService.categorize(event.description(), event.merchantName());
        return new Transaction(
                new TransactionId(UUID.fromString(event.id())),
                CustomerId.of(event.customerId()),
                new AccountId(UUID.fromString(event.accountId())),
                Money.of(event.amount(), event.currencyCode()),
                TransactionType.valueOf(event.type()),
                TransactionStatus.valueOf(event.status()),
                event.description(),
                category,
                event.merchantName(),
                DataSourceId.KAFKA,
                event.occurredAt()
        );
    }
}
