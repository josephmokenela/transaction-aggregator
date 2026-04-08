package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.SyncResult;

import java.time.Instant;
import java.util.List;

public record SyncResponse(
        String customerId,
        List<String> syncedSources,
        int totalTransactionsSynced,
        Instant syncedAt
) {

    static SyncResponse from(SyncResult result) {
        return new SyncResponse(
                result.customerId().value().toString(),
                result.syncedSources().stream().map(s -> s.value()).toList(),
                result.totalTransactionsSynced(),
                result.syncedAt()
        );
    }
}
