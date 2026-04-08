package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.CategorySummary;
import reactor.core.publisher.Flux;

public interface GetCategorySummaryUseCase {
    Flux<CategorySummary> getCategorySummary(GetCategorySummaryQuery query);
}
