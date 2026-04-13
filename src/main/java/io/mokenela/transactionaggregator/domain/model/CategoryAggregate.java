package io.mokenela.transactionaggregator.domain.model;

/**
 * Pre-aggregated transaction totals for a single (type, category) pair.
 *
 * <p>Produced by a SQL {@code GROUP BY} query in the persistence layer so that
 * summary calculations never materialise individual transaction rows into the JVM.
 * The count and total are exact for the full dataset, regardless of volume.</p>
 */
public record CategoryAggregate(
        TransactionType type,
        TransactionCategory category,
        Money totalAmount,
        long count
) {}
