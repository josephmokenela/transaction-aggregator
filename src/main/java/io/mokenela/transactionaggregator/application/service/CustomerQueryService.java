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
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional(readOnly = true)
    public Mono<PagedResponse<Customer>> listCustomers(PageRequest pageRequest) {
        return loadCustomerPort.loadAll(pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
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
                .flatMap(customer -> loadTransactionPort.aggregateByFilter(filter)
                        .collectList()
                        .map(aggregates -> buildCustomerSummaryFromAggregates(customer, query, aggregates)));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<CategorySummary> getCategorySummary(GetCategorySummaryQuery query) {
        log.debug("Building category breakdown for customer={} from={} to={}",
                query.customerId().value(), query.from(), query.to());

        var filter = TransactionFilter.builder()
                .customerId(query.customerId())
                .from(query.from())
                .to(query.to())
                .build();

        return loadCustomerPort.loadById(query.customerId())
                .switchIfEmpty(Mono.error(new CustomerNotFoundException(query.customerId())))
                .flatMapMany(ignored -> loadTransactionPort.aggregateByFilter(filter)
                        .collectList()
                        .flatMapIterable(this::buildCategorySummariesFromAggregates));
    }

    private CustomerSummary buildCustomerSummaryFromAggregates(Customer customer,
                                                                GetCustomerSummaryQuery query,
                                                                List<CategoryAggregate> aggregates) {
        if (aggregates.isEmpty()) {
            log.debug("No transactions found for customer={} in requested period", customer.id().value());
            var zero = Money.zero(defaultCurrency.getCurrencyCode());
            return new CustomerSummary(customer.id(), customer.name(), query.from(), query.to(),
                    zero, zero, zero, 0, List.of());
        }

        var currencyCode = aggregates.getFirst().totalAmount().currency().getCurrencyCode();
        var zero = Money.zero(currencyCode);

        var totalInflow = aggregates.stream()
                .filter(a -> a.type() == TransactionType.CREDIT)
                .map(CategoryAggregate::totalAmount)
                .reduce(zero, Money::add);

        var totalOutflow = aggregates.stream()
                .filter(a -> a.type() == TransactionType.DEBIT)
                .map(CategoryAggregate::totalAmount)
                .reduce(zero, Money::add);

        long totalCount = aggregates.stream().mapToLong(CategoryAggregate::count).sum();

        log.debug("Customer={} summary: {} transactions, inflow={} outflow={}",
                customer.id().value(), totalCount, totalInflow.amount(), totalOutflow.amount());

        return new CustomerSummary(
                customer.id(),
                customer.name(),
                query.from(),
                query.to(),
                totalInflow,
                totalOutflow,
                totalInflow.subtract(totalOutflow),
                totalCount,
                buildCategorySummariesFromAggregates(aggregates)
        );
    }

    private List<CategorySummary> buildCategorySummariesFromAggregates(List<CategoryAggregate> aggregates) {
        if (aggregates.isEmpty()) return List.of();

        var currencyCode = aggregates.getFirst().totalAmount().currency().getCurrencyCode();
        var zero = Money.zero(currencyCode);

        var grandTotal = aggregates.stream()
                .map(a -> a.totalAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Each aggregate is already one (type, category) group from SQL;
        // merge CREDIT and DEBIT rows for the same category into a single CategorySummary.
        Map<TransactionCategory, List<CategoryAggregate>> byCategory = aggregates.stream()
                .collect(Collectors.groupingBy(CategoryAggregate::category));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    var categoryTotal = entry.getValue().stream()
                            .map(CategoryAggregate::totalAmount)
                            .reduce(zero, Money::add);

                    var categoryAbsTotal = entry.getValue().stream()
                            .map(a -> a.totalAmount().amount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    long categoryCount = entry.getValue().stream()
                            .mapToLong(CategoryAggregate::count)
                            .sum();

                    var percentage = grandTotal.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : categoryAbsTotal.divide(grandTotal, 4, RoundingMode.HALF_EVEN)
                                              .multiply(BigDecimal.valueOf(100))
                                              .setScale(2, RoundingMode.HALF_EVEN);

                    return new CategorySummary(entry.getKey(), categoryTotal, categoryCount, percentage);
                })
                .sorted(Comparator.comparing(cs -> cs.totalAmount().amount(), Comparator.reverseOrder()))
                .toList();
    }
}
