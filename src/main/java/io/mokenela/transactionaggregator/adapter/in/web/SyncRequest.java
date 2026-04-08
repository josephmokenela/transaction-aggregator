package io.mokenela.transactionaggregator.adapter.in.web;

import java.time.Instant;

public record SyncRequest(
        String customerId,
        Instant from,
        Instant to
) {}
