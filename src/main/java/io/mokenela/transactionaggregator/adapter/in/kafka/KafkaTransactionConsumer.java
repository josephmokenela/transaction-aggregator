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
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Consumes transaction events from the Kafka topic and persists them.
 *
 * <p>Batch mode keeps throughput high — Kafka delivers a list of records per poll.
 * The container is configured with {@code AckMode.MANUAL}: the offset is committed
 * only after the reactive pipeline signals completion, so a fatal pipeline error
 * leaves the offset uncommitted and Kafka redelivers the batch on the next poll.</p>
 *
 * <p>Per-event failures are handled inside the {@code flatMap}: mapping errors and
 * save failures (after 3 R2DBC retries) are routed to the dead-letter topic so the
 * rest of the batch is unaffected. The outer subscribe error handler is a last-resort
 * safety net for unexpected pipeline faults — it deliberately withholds the ack,
 * letting Kafka trigger redelivery.</p>
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class KafkaTransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionConsumer.class);

    private final SaveTransactionPort saveTransactionPort;
    private final TransactionCategorizationService categorizationService;
    private final KafkaDltSender dltSender;
    private final MeterRegistry meterRegistry;
    private final String dltTopic;

    KafkaTransactionConsumer(SaveTransactionPort saveTransactionPort,
                             TransactionCategorizationService categorizationService,
                             KafkaDltSender dltSender,
                             MeterRegistry meterRegistry,
                             @Value("${app.kafka.topic}") String topic) {
        this.saveTransactionPort = saveTransactionPort;
        this.categorizationService = categorizationService;
        this.dltSender = dltSender;
        this.meterRegistry = meterRegistry;
        this.dltTopic = topic + ".DLT";
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "transaction-aggregator",
            batch = "true",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(List<KafkaTransactionEvent> events, Acknowledgment ack) {
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
                    return Mono.defer(() -> saveTransactionPort.save(transaction))
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
                .doOnComplete(() -> {
                    // All events were either saved or routed to DLT — safe to commit.
                    ack.acknowledge();
                    log.info("Batch of {} event(s) processed and offset committed", events.size());
                })
                .subscribe(
                        t -> {},
                        ex -> {
                            // Fatal pipeline error — deliberately withhold ack so Kafka redelivers
                            // the batch. Per-event errors are handled inside flatMap and never reach
                            // here, so this only fires for unexpected infrastructure faults.
                            log.error("Fatal error in Kafka consumer pipeline — offset withheld, batch will be redelivered", ex);
                            meterRegistry.counter("kafka.consumer.pipeline.errors").increment();
                        }
                );
    }

    private Mono<Transaction> sendToDlt(KafkaTransactionEvent event, String reason) {
        meterRegistry.counter("kafka.messages.dropped", "reason", reason).increment();
        return Mono.fromFuture(dltSender.send(dltTopic, event.id(), event))
                .doOnSuccess(r -> log.info("Routed event id={} to DLT ({})", event.id(), reason))
                .onErrorResume(ex -> {
                    // DLT is also unavailable — event is permanently lost; record it so
                    // the on-call engineer can detect and investigate via the metric alert.
                    log.error("Failed to route event id={} to DLT — event permanently lost: {}",
                            event.id(), ex.getMessage());
                    meterRegistry.counter("kafka.messages.lost", "reason", "dlt_unavailable").increment();
                    return Mono.empty();
                })
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
