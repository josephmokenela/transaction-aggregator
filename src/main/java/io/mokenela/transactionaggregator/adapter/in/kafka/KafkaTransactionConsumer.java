package io.mokenela.transactionaggregator.adapter.in.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.mokenela.transactionaggregator.application.service.TransactionCategorizationService;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
    private final KafkaTemplate<String, KafkaTransactionEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final String dltTopic;

    KafkaTransactionConsumer(SaveTransactionPort saveTransactionPort,
                             TransactionCategorizationService categorizationService,
                             KafkaTemplate<String, KafkaTransactionEvent> kafkaTemplate,
                             MeterRegistry meterRegistry,
                             @Value("${app.kafka.topic}") String topic) {
        this.saveTransactionPort = saveTransactionPort;
        this.categorizationService = categorizationService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.dltTopic = topic + ".DLT";
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
                .flatMap(event -> {
                    Transaction transaction;
                    try {
                        transaction = toDomain(event);
                    } catch (Exception ex) {
                        log.error("Failed to map event id={}, routing to DLT: {}", event.id(), ex.getMessage());
                        return sendToDlt(event, "mapping_error");
                    }
                    return saveTransactionPort.save(transaction)
                            .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                                    .filter(ex -> ex.getMessage() != null && ex.getMessage().contains("R2DBC Connection"))
                                    .doBeforeRetry(sig -> log.warn("Retrying save id={} (attempt {})",
                                            transaction.id().value(), sig.totalRetriesInARow() + 1)))
                            .onErrorResume(ex -> {
                                log.error("Failed to save id={} after retries, routing to DLT: {}",
                                        transaction.id().value(), ex.getMessage());
                                return sendToDlt(event, "save_error");
                            });
                }, 8)
                .doOnComplete(() -> log.info("Batch of {} event(s) processed", events.size()))
                .subscribe(
                        t -> {},
                        ex -> log.error("Fatal error in Kafka consumer pipeline", ex)
                );
    }

    private Mono<Transaction> sendToDlt(KafkaTransactionEvent event, String reason) {
        meterRegistry.counter("kafka.messages.dropped", "reason", reason).increment();
        return Mono.fromFuture(kafkaTemplate.send(dltTopic, event.id(), event).toCompletableFuture())
                .doOnSuccess(r -> log.info("Routed event id={} to DLT ({})", event.id(), reason))
                .doOnError(ex -> log.error("Failed to route event id={} to DLT: {}", event.id(), ex.getMessage()))
                .then(Mono.empty());
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
