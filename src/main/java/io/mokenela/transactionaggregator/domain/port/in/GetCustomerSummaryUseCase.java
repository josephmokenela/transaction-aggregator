package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.CustomerSummary;
import reactor.core.publisher.Mono;

public interface GetCustomerSummaryUseCase {
    Mono<CustomerSummary> getCustomerSummary(GetCustomerSummaryQuery query);
}
