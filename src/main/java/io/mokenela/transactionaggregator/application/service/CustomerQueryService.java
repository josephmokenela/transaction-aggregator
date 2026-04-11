package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.exception.CustomerNotFoundException;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadCustomerPort;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CustomerQueryService
        implements GetCustomerSummaryUseCase, GetCategorySummaryUseCase, ListCustomersUseCase {

    private static final Logger log = LoggerFactory.getLogger(CustomerQueryService.class);

    private final LoadCustomerPort loadCustomerPort;
    private final LoadTransactionPort loadTransactionPort;
    private final Currency defaultCurrency;

    public CustomerQueryService(LoadCustomerPort loadCustomerPort,
                                LoadTransactionPort loadTransactionPort,
                                @Value("${app.default-currency:GBP}") String defaultCurrencyCode) {
        this.loadCustomerPort = loadCustomerPort;
        this.loadTransactionPort = loadTransactionPort;
        this.defaultCurrency = Currency.getInstance(defaultCurrencyCode);
    }

    @Override
    public Flux<Customer> listCustomers() {
        return loadCustomerPort.loadAll();
    }

    @Override
    public Mono<CustomerSummary> getCustomerSummary(GetCustomerSummaryQuery query) {
        log.debug("Building summary for customer={} from={} to={}",
                query.customerId().value(), query.from(), query.to());

        var filter = TransactionFilter.builder()
                .customerId(query.customerId())
                .from(query.from())
                .to(query.to())
                .build();

        return loadCustomerPort.loadById(query.customerId())
                .switchIfEmpty(Mono.error(new CustomerNotFoundException(query.customerId())))
                .flatMap(customer -> loadTransactionPort.loadByFilter(filter, 10_000)
                        .collectList()
                        .map(transactions -> buildCustomerSummary(customer, query, transactions)));
    }

    @Override
    public Flux<CategorySummary> getCategorySummary(GetCategorySummaryQuery query) {
        log.debug("Building category breakdown for customer={} from={} to={}",
                query.customerId().value(), query.from(), query.to());

        var filter = TransactionFilter.builder()
                .customerId(query.customerId())
                .from(query.from())
                .to(query.to())
                .build();

        // Verify the customer exists before computing the summary
        return loadCustomerPort.loadById(query.customerId())
                .switchIfEmpty(Mono.error(new CustomerNotFoundException(query.customerId())))
                .flatMapMany(ignored -> loadTransactionPort.loadByFilter(filter, 10_000)
                        .collectList()
                        .flatMapIterable(this::buildCategorySummaries));
    }

    private CustomerSummary buildCustomerSummary(Customer customer, GetCustomerSummaryQuery query,
                                                  List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            log.debug("No transactions found for customer={} in requested period", customer.id().value());
            var zero = Money.zero(defaultCurrency.getCurrencyCode());
            return new CustomerSummary(customer.id(), customer.name(), query.from(), query.to(),
                    zero, zero, zero, 0, List.of());
        }

        var currencyCode = transactions.getFirst().amount().currency().getCurrencyCode();
        var zero = Money.zero(currencyCode);

        var totalInflow = transactions.stream()
                .filter(Transaction::isCredit)
                .map(Transaction::amount)
                .reduce(zero, Money::add);

        var totalOutflow = transactions.stream()
                .filter(Transaction::isDebit)
                .map(Transaction::amount)
                .reduce(zero, Money::add);

        var categorySummaries = buildCategorySummaries(transactions);

        log.debug("Customer={} summary: {} transactions, inflow={} outflow={}",
                customer.id().value(), transactions.size(), totalInflow.amount(), totalOutflow.amount());

        return new CustomerSummary(
                customer.id(),
                customer.name(),
                query.from(),
                query.to(),
                totalInflow,
                totalOutflow,
                totalInflow.subtract(totalOutflow),
                transactions.size(),
                categorySummaries
        );
    }

    private List<CategorySummary> buildCategorySummaries(List<Transaction> transactions) {
        if (transactions.isEmpty()) return List.of();

        var currencyCode = transactions.getFirst().amount().currency().getCurrencyCode();
        var zero = Money.zero(currencyCode);

        // Grand total across all transactions (used as the denominator for percentages)
        var grandTotal = transactions.stream()
                .map(t -> t.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<TransactionCategory, List<Transaction>> byCategory = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::category));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    var categoryTransactions = entry.getValue();
                    var categoryTotal = categoryTransactions.stream()
                            .map(Transaction::amount)
                            .reduce(zero, Money::add);

                    var categoryAbsTotal = categoryTransactions.stream()
                            .map(t -> t.amount().amount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    var percentage = grandTotal.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : categoryAbsTotal.divide(grandTotal, 4, RoundingMode.HALF_EVEN)
                                              .multiply(BigDecimal.valueOf(100))
                                              .setScale(2, RoundingMode.HALF_EVEN);

                    return new CategorySummary(entry.getKey(), categoryTotal, categoryTransactions.size(), percentage);
                })
                .sorted(Comparator.comparing(cs -> cs.totalAmount().amount(), Comparator.reverseOrder()))
                .toList();
    }
}
