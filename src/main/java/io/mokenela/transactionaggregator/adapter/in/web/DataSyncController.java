package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.DataSourceId;
import io.mokenela.transactionaggregator.domain.port.in.ListAvailableSourcesUseCase;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsCommand;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
@Tag(name = "Data Sync", description = "Trigger ingestion from mock data sources")
class DataSyncController {

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
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sync completed"),
                    @ApiResponse(responseCode = "400", description = "Validation error")
            }
    )
    Mono<SyncResponse> sync(@Valid @RequestBody SyncRequest request) {
        var command = new SyncTransactionsCommand(
                CustomerId.of(request.customerId()),
                request.from(),
                request.to()
        );
        return syncTransactionsUseCase.sync(command).map(SyncResponse::from);
    }

    @GetMapping("/sources")
    @Operation(summary = "List available data sources", description = "Returns the IDs of all registered data source adapters.")
    List<DataSourceId> listSources() {
        return listAvailableSourcesUseCase.listAvailableSources();
    }
}
