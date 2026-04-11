package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Record, retrieve, search, and aggregate transactions")
class TransactionController {

    private final RecordTransactionUseCase recordTransactionUseCase;
    private final GetTransactionUseCase getTransactionUseCase;
    private final AggregateTransactionsUseCase aggregateTransactionsUseCase;
    private final SearchTransactionsUseCase searchTransactionsUseCase;

    TransactionController(RecordTransactionUseCase recordTransactionUseCase,
                          GetTransactionUseCase getTransactionUseCase,
                          AggregateTransactionsUseCase aggregateTransactionsUseCase,
                          SearchTransactionsUseCase searchTransactionsUseCase) {
        this.recordTransactionUseCase = recordTransactionUseCase;
        this.getTransactionUseCase = getTransactionUseCase;
        this.aggregateTransactionsUseCase = aggregateTransactionsUseCase;
        this.searchTransactionsUseCase = searchTransactionsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Record a manual transaction",
            description = "Creates a new transaction with automatic category assignment based on description and merchant.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Transaction recorded"),
                    @ApiResponse(responseCode = "400", description = "Validation error")
            }
    )
    Mono<TransactionResponse> recordTransaction(@Valid @RequestBody RecordTransactionRequest request) {
        var command = new RecordTransactionCommand(
                CustomerId.of(request.customerId()),
                AccountId.of(request.accountId()),
                Money.of(request.amount(), request.currency()),
                request.type(),
                request.description(),
                request.merchantName()
        );
        return recordTransactionUseCase.recordTransaction(command).map(TransactionResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a transaction by ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Transaction found"),
                    @ApiResponse(responseCode = "404", description = "Transaction not found")
            }
    )
    Mono<TransactionResponse> getTransaction(@PathVariable String id) {
        return getTransactionUseCase.getTransaction(new GetTransactionQuery(TransactionId.of(id)))
                .map(TransactionResponse::from);
    }

    @GetMapping
    @Operation(
            summary = "Search transactions",
            description = "Returns transactions matching the supplied filters. All parameters are optional. " +
                          "Results are capped by the `limit` parameter (default 50)."
    )
    Flux<TransactionResponse> search(
            @Parameter(description = "Filter by customer UUID") @RequestParam(required = false) String customerId,
            @Parameter(description = "Filter by account UUID") @RequestParam(required = false) String accountId,
            @Parameter(description = "Filter by category") @RequestParam(required = false) TransactionCategory category,
            @Parameter(description = "Filter by CREDIT or DEBIT") @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Filter by data source ID") @RequestParam(required = false) String dataSourceId,
            @Parameter(description = "Keyword match on description or merchant name") @RequestParam(required = false) String keyword,
            @Parameter(description = "Range start (ISO-8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "Range end (ISO-8601)") @RequestParam(required = false) Instant to,
            @Parameter(description = "Maximum results to return (1–1000)") @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int limit) {

        var filter = TransactionFilter.builder()
                .customerId(customerId != null ? CustomerId.of(customerId) : null)
                .accountId(accountId != null ? AccountId.of(accountId) : null)
                .category(category)
                .type(type)
                .dataSourceId(dataSourceId != null ? new DataSourceId(dataSourceId) : null)
                .keyword(keyword)
                .from(from)
                .to(to)
                .build();

        return searchTransactionsUseCase.search(new SearchTransactionsQuery(filter, limit))
                .map(TransactionResponse::from);
    }

    @GetMapping("/aggregate")
    @Operation(
            summary = "Aggregate transactions by period",
            description = "Groups transactions for an account within the given time window into HOURLY, DAILY, " +
                          "WEEKLY, MONTHLY, or YEARLY buckets, returning credit/debit totals and net per bucket."
    )
    Mono<AggregatedTransactionsResponse> aggregate(
            @Parameter(description = "Account UUID", required = true) @RequestParam String accountId,
            @Parameter(description = "Aggregation granularity", required = true) @RequestParam AggregationPeriod period,
            @Parameter(description = "Range start (ISO-8601)", required = true) @RequestParam Instant from,
            @Parameter(description = "Range end (ISO-8601)", required = true) @RequestParam Instant to) {

        var query = new AggregateTransactionsQuery(AccountId.of(accountId), period, from, to);
        return aggregateTransactionsUseCase.aggregate(query).map(AggregatedTransactionsResponse::from);
    }
}
