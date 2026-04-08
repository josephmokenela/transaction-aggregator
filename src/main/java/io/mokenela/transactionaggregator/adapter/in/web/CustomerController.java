package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import io.mokenela.transactionaggregator.domain.model.TransactionFilter;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadCustomerPort;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/customers")
class CustomerController {

    private final LoadCustomerPort loadCustomerPort;
    private final GetCustomerSummaryUseCase getCustomerSummaryUseCase;
    private final GetCategorySummaryUseCase getCategorySummaryUseCase;
    private final SearchTransactionsUseCase searchTransactionsUseCase;

    CustomerController(LoadCustomerPort loadCustomerPort,
                       GetCustomerSummaryUseCase getCustomerSummaryUseCase,
                       GetCategorySummaryUseCase getCategorySummaryUseCase,
                       SearchTransactionsUseCase searchTransactionsUseCase) {
        this.loadCustomerPort = loadCustomerPort;
        this.getCustomerSummaryUseCase = getCustomerSummaryUseCase;
        this.getCategorySummaryUseCase = getCategorySummaryUseCase;
        this.searchTransactionsUseCase = searchTransactionsUseCase;
    }

    /** List all known customers. */
    @GetMapping
    Flux<CustomerResponse> listCustomers() {
        return loadCustomerPort.loadAll().map(CustomerResponse::from);
    }

    /** Full financial summary for a customer over a time range. */
    @GetMapping("/{customerId}/summary")
    Mono<CustomerSummaryResponse> getCustomerSummary(
            @PathVariable String customerId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        var query = new GetCustomerSummaryQuery(CustomerId.of(customerId), from, to);
        return getCustomerSummaryUseCase.getCustomerSummary(query).map(CustomerSummaryResponse::from);
    }

    /** Category-level spend breakdown for a customer. */
    @GetMapping("/{customerId}/categories")
    Flux<CategorySummaryResponse> getCategorySummary(
            @PathVariable String customerId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        var query = new GetCategorySummaryQuery(CustomerId.of(customerId), from, to);
        return getCategorySummaryUseCase.getCategorySummary(query).map(CategorySummaryResponse::from);
    }

    /** Transactions for a customer, optionally filtered by category. */
    @GetMapping("/{customerId}/transactions")
    Flux<TransactionResponse> getTransactions(
            @PathVariable String customerId,
            @RequestParam(required = false) TransactionCategory category,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "50") int limit) {

        var filter = TransactionFilter.builder()
                .customerId(CustomerId.of(customerId))
                .category(category)
                .from(from)
                .to(to)
                .build();

        return searchTransactionsUseCase.search(new SearchTransactionsQuery(filter, limit))
                .map(TransactionResponse::from);
    }
}
