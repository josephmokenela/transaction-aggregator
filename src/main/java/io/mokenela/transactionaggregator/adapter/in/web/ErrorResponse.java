package io.mokenela.transactionaggregator.adapter.in.web;

import java.time.Instant;

public record ErrorResponse(
        String errorCode,
        String message,
        Instant timestamp
) {

    static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Instant.now());
    }
}
