package io.mokenela.transactionaggregator.adapter.in.web;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.DataSourceId;
import io.mokenela.transactionaggregator.domain.port.in.ListAvailableSourcesUseCase;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsCommand;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
@Tag(name = "Data Sync", description = "Trigger ingestion from mock data sources")
class DataSyncController {

    // Allow at most 5 concurrent sync requests — each sync fans out to all sources and is DB-heavy
    private static final RateLimiter rateLimiter = RateLimiter.of("sync",
            RateLimiterConfig.custom()
                    .limitForPeriod(5)
                    .limitRefreshPeriod(Duration.ofSeconds(10))
                    .timeoutDuration(Duration.ZERO)  // fail immediately if limit exceeded
                    .build());

    private final SyncTransactionsUseCase syncTransactionsUseCase;
    private final ListAvailableSourcesUseCase listAvailableSourcesUseCase;

    DataSyncController(SyncTransactionsUseCase syncTransactionsUseCase,
                       ListAvailableSourcesUseCase listAvailableSourcesUseCase) {
        this.syncTransactionsUseCase = syncTransactionsUseCase;
        this.listAvailableSourcesUseCase = listAvailableSourcesUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Sync transactions from all data sources",
            description = """
                    Triggers a parallel fetch from all registered data sources for the given customer
                    and time window. Transactions are automatically categorised on ingestion.
                    The operation is idempotent — re-syncing the same period overwrites existing records by ID.
                    Rate limited to 5 requests per 10 seconds.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sync completed"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "429", description = "Too many sync requests — try again shortly")
            }
    )
    Mono<SyncResponse> sync(@Valid @RequestBody SyncRequest request) {
        var command = new SyncTransactionsCommand(
                CustomerId.of(request.customerId()),
                request.from(),
                request.to()
        );
        return syncTransactionsUseCase.sync(command)
                .map(SyncResponse::from)
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorMap(io.github.resilience4j.ratelimiter.RequestNotPermitted.class,
                        ex -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                "Too many sync requests — try again shortly"));
    }

    @GetMapping("/sources")
    @Operation(summary = "List available data sources", description = "Returns the IDs of all registered data source adapters.")
    List<DataSourceId> listSources() {
        return listAvailableSourcesUseCase.listAvailableSources();
    }
}
