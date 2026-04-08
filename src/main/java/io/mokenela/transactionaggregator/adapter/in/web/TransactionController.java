package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/transactions")
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
    Mono<TransactionResponse> getTransaction(@PathVariable String id) {
        return getTransactionUseCase.getTransaction(new GetTransactionQuery(TransactionId.of(id)))
                .map(TransactionResponse::from);
    }

    /**
     * Search transactions with flexible filters.
     *
     * @param customerId   filter by customer (optional)
     * @param accountId    filter by account (optional)
     * @param category     filter by category (optional)
     * @param type         filter by CREDIT or DEBIT (optional)
     * @param dataSourceId filter by source (optional)
     * @param keyword      keyword match on description or merchant name (optional)
     * @param from         start of date range (optional)
     * @param to           end of date range (optional)
     * @param limit        max results, default 50
     */
    @GetMapping
    Flux<TransactionResponse> search(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) TransactionCategory category,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String dataSourceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "50") int limit) {

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
    Mono<AggregatedTransactionsResponse> aggregate(
            @RequestParam String accountId,
            @RequestParam AggregationPeriod period,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        AggregateTransactionsQuery query = new AggregateTransactionsQuery(
                AccountId.of(accountId),
                period,
                from,
                to
        );
        return aggregateTransactionsUseCase.aggregate(query).map(AggregatedTransactionsResponse::from);
    }
}
