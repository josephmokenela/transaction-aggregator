package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.AggregationPeriod;
import io.mokenela.transactionaggregator.domain.model.Money;
import io.mokenela.transactionaggregator.domain.model.TransactionId;
import io.mokenela.transactionaggregator.domain.port.in.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

    private final RecordTransactionUseCase recordTransactionUseCase;
    private final GetTransactionUseCase getTransactionUseCase;
    private final AggregateTransactionsUseCase aggregateTransactionsUseCase;

    TransactionController(RecordTransactionUseCase recordTransactionUseCase,
                          GetTransactionUseCase getTransactionUseCase,
                          AggregateTransactionsUseCase aggregateTransactionsUseCase) {
        this.recordTransactionUseCase = recordTransactionUseCase;
        this.getTransactionUseCase = getTransactionUseCase;
        this.aggregateTransactionsUseCase = aggregateTransactionsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Mono<TransactionResponse> recordTransaction(@RequestBody RecordTransactionRequest request) {
        var command = new RecordTransactionCommand(
                AccountId.of(request.accountId()),
                Money.of(request.amount(), request.currency()),
                request.type(),
                request.description()
        );
        return recordTransactionUseCase.recordTransaction(command)
                .map(TransactionResponse::from);
    }

    @GetMapping("/{id}")
    Mono<TransactionResponse> getTransaction(@PathVariable String id) {
        var query = new GetTransactionQuery(TransactionId.of(id));
        return getTransactionUseCase.getTransaction(query)
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
        return aggregateTransactionsUseCase.aggregate(query)
                .map(AggregatedTransactionsResponse::from);
    }
}
