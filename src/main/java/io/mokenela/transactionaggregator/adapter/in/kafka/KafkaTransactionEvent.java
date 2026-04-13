package io.mokenela.transactionaggregator.adapter.in.kafka;

import io.mokenela.transactionaggregator.util.Mask;

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
) {

    @Override
    public String toString() {
        return "KafkaTransactionEvent[id=" + id +
               ", customerId=" + customerId +
               ", accountId=" + accountId +
               ", amount=" + Mask.amount(amount) +
               ", currencyCode=" + currencyCode +
               ", type=" + type +
               ", status=" + status +
               ", description=" + Mask.text(description) +
               ", merchantName=" + Mask.text(merchantName) +
               ", occurredAt=" + occurredAt + "]";
    }
}
