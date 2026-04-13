package io.mokenela.transactionaggregator.adapter.out.kafka;

import io.mokenela.transactionaggregator.adapter.in.kafka.KafkaTransactionEvent;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.port.out.TransactionGeneratorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates synthetic transactions and publishes them to the Kafka topic.
 * Used to simulate high-volume data ingestion for load testing / demos.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class KafkaTransactionProducer implements TransactionGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionProducer.class);

    private static final List<String[]> MERCHANT_TEMPLATES = List.of(
            new String[]{"CREDIT", "COMPLETED", "Monthly Salary",       "Employer Ltd"},
            new String[]{"DEBIT",  "COMPLETED", "Grocery Shopping",     "Tesco"},
            new String[]{"DEBIT",  "COMPLETED", "Coffee Shop",          "Starbucks"},
            new String[]{"DEBIT",  "COMPLETED", "Online Shopping",      "Amazon"},
            new String[]{"DEBIT",  "COMPLETED", "Electricity Bill",     "EnergySupplier"},
            new String[]{"DEBIT",  "COMPLETED", "Streaming Service",    "Netflix"},
            new String[]{"DEBIT",  "PENDING",   "Restaurant",           "Pizza Express"},
            new String[]{"CREDIT", "COMPLETED", "Refund",               "Amazon"},
            new String[]{"DEBIT",  "COMPLETED", "Fuel",                 "Shell"},
            new String[]{"DEBIT",  "FAILED",    "International Transfer", null}
    );

    private final KafkaTemplate<String, KafkaTransactionEvent> kafkaTemplate;
    private final String topic;

    KafkaTransactionProducer(KafkaTemplate<String, KafkaTransactionEvent> kafkaTemplate,
                             @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Publishes {@code count} synthetic transactions for the given customer.
     * Runs on a bounded-elastic scheduler so it does not block the event loop.
     */
    @Override
    public Mono<Integer> generate(CustomerId customerId, int count) {
        log.info("Generating {} transaction(s) for customer={}", count, customerId.value());
        var random = new Random();
        var accountId = UUID.randomUUID().toString();

        return Flux.range(0, count)
                .map(i -> buildEvent(customerId.value().toString(), accountId, random, i))
                .flatMap(event -> Mono.fromFuture(
                        kafkaTemplate.send(topic, event.id(), event).toCompletableFuture()
                ).thenReturn(1), 32)
                .reduce(0, Integer::sum)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(n -> log.info("Published {} transaction(s) to topic={}", n, topic))
                .doOnError(ex -> log.error("Kafka publish failed: {}", ex.getMessage(), ex));
    }

    private KafkaTransactionEvent buildEvent(String customerId, String accountId,
                                             Random random, int index) {
        var template = MERCHANT_TEMPLATES.get(index % MERCHANT_TEMPLATES.size());
        var amount = BigDecimal.valueOf(random.nextDouble() * 4990 + 10)
                .setScale(2, RoundingMode.HALF_UP);
        var occurredAt = Instant.now().minus(random.nextInt(365), ChronoUnit.DAYS);

        return new KafkaTransactionEvent(
                UUID.randomUUID().toString(),
                customerId,
                accountId,
                amount,
                "GBP",
                template[0],
                template[1],
                template[2],
                template[3],
                occurredAt
        );
    }
}
