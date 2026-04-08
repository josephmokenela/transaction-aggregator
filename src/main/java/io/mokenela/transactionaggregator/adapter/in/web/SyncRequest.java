package io.mokenela.transactionaggregator.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SyncRequest(
        @NotBlank(message = "customerId is required") String customerId,
        @NotNull(message = "from is required") Instant from,
        @NotNull(message = "to is required") Instant to
) {}
