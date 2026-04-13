package io.mokenela.transactionaggregator.domain.port.out;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import reactor.core.publisher.Mono;

/**
 * Outbound port for generating and publishing synthetic transactions.
 *
 * <p>Exists to allow inbound adapters (e.g. a dev-only HTTP trigger) to request
 * transaction generation without depending on the Kafka adapter implementation
 * directly, preserving the hexagonal layer boundary.</p>
 */
public interface TransactionGeneratorPort {

    /**
     * Generates and publishes {@code count} synthetic transactions for the given customer.
     *
     * @return the number of transactions successfully published
     */
    Mono<Integer> generate(CustomerId customerId, int count);
}
