package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import io.mokenela.transactionaggregator.domain.model.TransactionFilter;
import io.mokenela.transactionaggregator.domain.port.in.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/customers")
class CustomerController {

    private final ListCustomersUseCase listCustomersUseCase;
    private final GetCustomerSummaryUseCase getCustomerSummaryUseCase;
    private final GetCategorySummaryUseCase getCategorySummaryUseCase;
    private final SearchTransactionsUseCase searchTransactionsUseCase;

    CustomerController(ListCustomersUseCase listCustomersUseCase,
                       GetCustomerSummaryUseCase getCustomerSummaryUseCase,
                       GetCategorySummaryUseCase getCategorySummaryUseCase,
                       SearchTransactionsUseCase searchTransactionsUseCase) {
        this.listCustomersUseCase = listCustomersUseCase;
        this.getCustomerSummaryUseCase = getCustomerSummaryUseCase;
        this.getCategorySummaryUseCase = getCategorySummaryUseCase;
        this.searchTransactionsUseCase = searchTransactionsUseCase;
    }

    /** Returns all known customers. */
    @GetMapping
    Flux<CustomerResponse> listCustomers() {
        return listCustomersUseCase.listCustomers().map(CustomerResponse::from);
    }

    /** Full financial summary — total inflow/outflow, net position, and category breakdown. */
    @GetMapping("/{customerId}/summary")
    Mono<CustomerSummaryResponse> getCustomerSummary(
            @PathVariable String customerId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return getCustomerSummaryUseCase
                .getCustomerSummary(new GetCustomerSummaryQuery(CustomerId.of(customerId), from, to))
                .map(CustomerSummaryResponse::from);
    }

    /** Category-level spend breakdown with percentage of total activity. */
    @GetMapping("/{customerId}/categories")
    Flux<CategorySummaryResponse> getCategorySummary(
            @PathVariable String customerId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return getCategorySummaryUseCase
                .getCategorySummary(new GetCategorySummaryQuery(CustomerId.of(customerId), from, to))
                .map(CategorySummaryResponse::from);
    }

    /** Transactions for a customer, optionally filtered by category, with configurable limit. */
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

        return searchTransactionsUseCase
                .search(new SearchTransactionsQuery(filter, limit))
                .map(TransactionResponse::from);
    }
}
