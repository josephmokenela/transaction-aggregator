package io.mokenela.transactionaggregator.adapter.in.kafka;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes a failed event to the Dead Letter Topic.
 *
 * <p>Extracted as an interface so that {@link KafkaTransactionConsumer} does not
 * depend directly on {@link org.springframework.kafka.core.KafkaTemplate}, which
 * is a heavy Spring infrastructure class. The production wiring (via lambda in
 * {@link io.mokenela.transactionaggregator.config.KafkaTopicConfig}) delegates
 * to {@code KafkaTemplate.send()}. Tests can supply a plain mock.
 */
@FunctionalInterface
public interface KafkaDltSender {
    CompletableFuture<?> send(String dltTopic, String key, KafkaTransactionEvent event);
}
