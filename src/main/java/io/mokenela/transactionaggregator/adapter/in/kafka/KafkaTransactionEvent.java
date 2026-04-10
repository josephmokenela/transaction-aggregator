package io.mokenela.transactionaggregator.adapter.in.kafka;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JSON payload published to the Kafka transactions topic.
 * Uses plain types so Jackson can deserialise without custom modules.
 */
public record KafkaTransactionEvent(
        String id,
        String customerId,
        String accountId,
        BigDecimal amount,
        String currencyCode,
        String type,       // CREDIT | DEBIT
        String status,     // COMPLETED | PENDING | FAILED
        String description,
        String merchantName,
        Instant occurredAt
) {}
