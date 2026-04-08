package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.DataSourceId;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsCommand;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsUseCase;
import io.mokenela.transactionaggregator.domain.port.out.FetchTransactionsPort;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
class DataSyncController {

    private final SyncTransactionsUseCase syncTransactionsUseCase;
    private final List<FetchTransactionsPort> sources;

    DataSyncController(SyncTransactionsUseCase syncTransactionsUseCase,
                       List<FetchTransactionsPort> sources) {
        this.syncTransactionsUseCase = syncTransactionsUseCase;
        this.sources = sources;
    }

    /**
     * Trigger a full sync from all data sources for a customer over a given time range.
     * Transactions are automatically categorized on ingestion.
     */
    @PostMapping
    Mono<SyncResponse> sync(@RequestBody SyncRequest request) {
        var command = new SyncTransactionsCommand(
                CustomerId.of(request.customerId()),
                request.from(),
                request.to()
        );
        return syncTransactionsUseCase.sync(command).map(SyncResponse::from);
    }

    /** List the registered data sources available for sync. */
    @GetMapping("/sources")
    List<DataSourceId> listSources() {
        return sources.stream().map(FetchTransactionsPort::sourceId).toList();
    }
}
