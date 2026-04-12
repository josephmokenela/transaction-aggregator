package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            description = "Creates a new transaction. Customers may only record for their own customerId; " +
                          "admins may record for any customer.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Transaction recorded"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "403", description = "customerId does not match authenticated user")
            }
    )
    Mono<TransactionResponse> recordTransaction(@Valid @RequestBody RecordTransactionRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        if (!JwtUtils.isAdmin(jwt) && !jwt.getSubject().equals(request.customerId())) {
            return Mono.error(new AccessDeniedException("You can only record transactions for yourself"));
        }
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
            description = "Customers may only retrieve their own transactions; admins may retrieve any.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Transaction found"),
                    @ApiResponse(responseCode = "403", description = "Transaction belongs to another customer"),
                    @ApiResponse(responseCode = "404", description = "Transaction not found")
            }
    )
    Mono<TransactionResponse> getTransaction(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return getTransactionUseCase.getTransaction(new GetTransactionQuery(TransactionId.of(id)))
                .flatMap(tx -> {
                    if (JwtUtils.isAdmin(jwt) || jwt.getSubject().equals(tx.customerId().value().toString())) {
                        return Mono.just(TransactionResponse.from(tx));
                    }
                    return Mono.error(new AccessDeniedException("You can only access your own transactions"));
                });
    }

    @GetMapping
    @Operation(
            summary = "Search transactions",
            description = "Returns transactions matching the supplied filters. " +
                          "Customers are automatically scoped to their own data regardless of the customerId parameter. " +
                          "Admins may query any customer. Results are capped by `limit` (default 50).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    Flux<TransactionResponse> search(
            @Parameter(description = "Filter by customer UUID (ignored for non-admins)") @RequestParam(required = false) String customerId,
            @Parameter(description = "Filter by account UUID") @RequestParam(required = false) String accountId,
            @Parameter(description = "Filter by category") @RequestParam(required = false) TransactionCategory category,
            @Parameter(description = "Filter by CREDIT or DEBIT") @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Filter by data source ID") @RequestParam(required = false) String dataSourceId,
            @Parameter(description = "Keyword match on description or merchant name") @RequestParam(required = false) String keyword,
            @Parameter(description = "Range start (ISO-8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "Range end (ISO-8601)") @RequestParam(required = false) Instant to,
            @Parameter(description = "Maximum results to return (1–1000)") @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int limit,
            @AuthenticationPrincipal Jwt jwt) {

        // Customers are always scoped to their own ID; admins can pass any ID (or none for all)
        String effectiveCustomerId = JwtUtils.effectiveCustomerId(jwt, customerId);

        var filter = TransactionFilter.builder()
                .customerId(effectiveCustomerId != null ? CustomerId.of(effectiveCustomerId) : null)
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
                          "WEEKLY, MONTHLY, or YEARLY buckets, returning credit/debit totals and net per bucket.",
            security = @SecurityRequirement(name = "bearerAuth")
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
