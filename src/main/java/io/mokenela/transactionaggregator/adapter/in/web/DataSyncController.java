package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.DataSourceId;
import io.mokenela.transactionaggregator.domain.port.in.ListAvailableSourcesUseCase;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsCommand;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
class DataSyncController {

    private final SyncTransactionsUseCase syncTransactionsUseCase;
    private final ListAvailableSourcesUseCase listAvailableSourcesUseCase;

    DataSyncController(SyncTransactionsUseCase syncTransactionsUseCase,
                       ListAvailableSourcesUseCase listAvailableSourcesUseCase) {
        this.syncTransactionsUseCase = syncTransactionsUseCase;
        this.listAvailableSourcesUseCase = listAvailableSourcesUseCase;
    }

    /**
     * Triggers a full sync from all registered data sources for a customer over the given period.
     * Transactions are automatically categorised on ingestion.
     * Idempotent — repeated syncs for the same period overwrite existing records by ID.
     */
    @PostMapping
    Mono<SyncResponse> sync(@Valid @RequestBody SyncRequest request) {
        var command = new SyncTransactionsCommand(
                CustomerId.of(request.customerId()),
                request.from(),
                request.to()
        );
        return syncTransactionsUseCase.sync(command).map(SyncResponse::from);
    }

    /** Lists the data source identifiers currently registered with the system. */
    @GetMapping("/sources")
    List<DataSourceId> listSources() {
        return listAvailableSourcesUseCase.listAvailableSources();
    }
}
