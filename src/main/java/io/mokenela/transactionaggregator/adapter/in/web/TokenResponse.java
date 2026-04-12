package io.mokenela.transactionaggregator.adapter.in.web;

public record TokenResponse(
        String token,
        String type,
        long expiresIn   // seconds
) {}
