package io.mokenela.transactionaggregator.domain.model;

import java.time.Instant;
import java.util.List;

public record SyncResult(
        CustomerId customerId,
        List<DataSourceId> syncedSources,
        int totalTransactionsSynced,
        Instant syncedAt
) {}
