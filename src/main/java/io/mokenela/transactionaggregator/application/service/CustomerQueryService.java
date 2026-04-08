package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadCustomerPort;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CustomerQueryService implements GetCustomerSummaryUseCase, GetCategorySummaryUseCase {

    private final LoadCustomerPort loadCustomerPort;
    private final LoadTransactionPort loadTransactionPort;

    public CustomerQueryService(LoadCustomerPort loadCustomerPort,
                                LoadTransactionPort loadTransactionPort) {
        this.loadCustomerPort = loadCustomerPort;
        this.loadTransactionPort = loadTransactionPort;
    }

    @Override
    public Mono<CustomerSummary> getCustomerSummary(GetCustomerSummaryQuery query) {
        var filter = TransactionFilter.builder()
                .customerId(query.customerId())
                .from(query.from())
                .to(query.to())
                .build();

        return loadCustomerPort.loadById(query.customerId())
                .flatMap(customer -> loadTransactionPort.loadByFilter(filter)
                        .collectList()
                        .map(transactions -> buildCustomerSummary(customer, query, transactions)));
    }

    @Override
    public Flux<CategorySummary> getCategorySummary(GetCategorySummaryQuery query) {
        var filter = TransactionFilter.builder()
                .customerId(query.customerId())
                .from(query.from())
                .to(query.to())
                .build();

        return loadTransactionPort.loadByFilter(filter)
                .collectList()
                .flatMapMany(transactions -> Flux.fromIterable(buildCategorySummaries(transactions)));
    }

    private CustomerSummary buildCustomerSummary(Customer customer, GetCustomerSummaryQuery query,
                                                  List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return emptyCustomerSummary(customer, query);
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

        return new CustomerSummary(
                customer.id(),
                customer.name(),
                query.from(),
                query.to(),
                totalInflow,
                totalOutflow,
                totalInflow.subtract(totalOutflow),
                transactions.size(),
                buildCategorySummaries(transactions)
        );
    }

    private List<CategorySummary> buildCategorySummaries(List<Transaction> transactions) {
        if (transactions.isEmpty()) return List.of();

        var currencyCode = transactions.getFirst().amount().currency().getCurrencyCode();
        var zero = Money.zero(currencyCode);

        var totalSpend = transactions.stream()
                .filter(Transaction::isDebit)
                .map(t -> t.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<TransactionCategory, List<Transaction>> byCategory = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::category));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    var txns = entry.getValue();
                    var total = txns.stream()
                            .map(Transaction::amount)
                            .reduce(zero, Money::add);

                    var debitTotal = txns.stream()
                            .filter(Transaction::isDebit)
                            .map(t -> t.amount().amount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    var percentage = totalSpend.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : debitTotal.divide(totalSpend, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP);

                    return new CategorySummary(entry.getKey(), total, txns.size(), percentage);
                })
                .sorted(Comparator.comparing(cs -> cs.totalAmount().amount(), Comparator.reverseOrder()))
                .toList();
    }

    private CustomerSummary emptyCustomerSummary(Customer customer, GetCustomerSummaryQuery query) {
        var zero = Money.zero("GBP");
        return new CustomerSummary(customer.id(), customer.name(), query.from(), query.to(),
                zero, zero, zero, 0, List.of());
    }
}
