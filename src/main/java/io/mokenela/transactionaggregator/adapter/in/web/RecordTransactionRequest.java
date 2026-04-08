package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordTransactionRequest(
        @NotBlank(message = "customerId is required") String customerId,
        @NotBlank(message = "accountId is required") String accountId,
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero") BigDecimal amount,
        @NotBlank(message = "currency is required") String currency,
        @NotNull(message = "type is required") TransactionType type,
        @NotBlank(message = "description is required") String description,
        String merchantName   // optional — null omitted from JSON response
) {}
