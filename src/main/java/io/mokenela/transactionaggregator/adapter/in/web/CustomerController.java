package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import io.mokenela.transactionaggregator.domain.model.TransactionFilter;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer lookup and financial summaries")
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

    @GetMapping
    @Operation(summary = "List all customers (admin only)",
               security = @SecurityRequirement(name = "bearerAuth"))
    Flux<CustomerResponse> listCustomers() {
        // Access restricted to ROLE_ADMIN by SecurityConfig path rule
        return listCustomersUseCase.listCustomers().map(CustomerResponse::from);
    }

    @GetMapping("/{customerId}/summary")
    @Operation(
            summary = "Customer financial summary",
            description = "Total inflow/outflow, net position, and category breakdown for the given period. " +
                          "Customers may only access their own summary; admins may access any.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Summary returned"),
                    @ApiResponse(responseCode = "403", description = "Accessing another customer's data"),
                    @ApiResponse(responseCode = "404", description = "Customer not found")
            }
    )
    Mono<CustomerSummaryResponse> getCustomerSummary(
            @PathVariable String customerId,
            @Parameter(description = "Range start (ISO-8601)", required = true) @RequestParam Instant from,
            @Parameter(description = "Range end (ISO-8601)", required = true) @RequestParam Instant to,
            @AuthenticationPrincipal Jwt jwt) {

        if (!JwtUtils.isAdmin(jwt) && !jwt.getSubject().equals(customerId)) {
            return Mono.error(new AccessDeniedException("You can only access your own summary"));
        }
        return getCustomerSummaryUseCase
                .getCustomerSummary(new GetCustomerSummaryQuery(CustomerId.of(customerId), from, to))
                .map(CustomerSummaryResponse::from);
    }

    @GetMapping("/{customerId}/categories")
    @Operation(
            summary = "Category spend breakdown",
            description = "Per-category totals and percentage of overall activity for the given period. " +
                          "Customers may only access their own breakdown; admins may access any.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    Flux<CategorySummaryResponse> getCategorySummary(
            @PathVariable String customerId,
            @Parameter(description = "Range start (ISO-8601)", required = true) @RequestParam Instant from,
            @Parameter(description = "Range end (ISO-8601)", required = true) @RequestParam Instant to,
            @AuthenticationPrincipal Jwt jwt) {

        if (!JwtUtils.isAdmin(jwt) && !jwt.getSubject().equals(customerId)) {
            return Flux.error(new AccessDeniedException("You can only access your own category breakdown"));
        }
        return getCategorySummaryUseCase
                .getCategorySummary(new GetCategorySummaryQuery(CustomerId.of(customerId), from, to))
                .map(CategorySummaryResponse::from);
    }

    @GetMapping("/{customerId}/transactions")
    @Operation(
            summary = "Customer transactions",
            description = "Transactions for a customer, optionally filtered by category and date range. " +
                          "Customers may only access their own transactions; admins may access any.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    Flux<TransactionResponse> getTransactions(
            @PathVariable String customerId,
            @Parameter(description = "Filter by category") @RequestParam(required = false) TransactionCategory category,
            @Parameter(description = "Range start (ISO-8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "Range end (ISO-8601)") @RequestParam(required = false) Instant to,
            @Parameter(description = "Maximum results to return (1–1000)") @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int limit,
            @AuthenticationPrincipal Jwt jwt) {

        if (!JwtUtils.isAdmin(jwt) && !jwt.getSubject().equals(customerId)) {
            return Flux.error(new AccessDeniedException("You can only access your own transactions"));
        }
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
