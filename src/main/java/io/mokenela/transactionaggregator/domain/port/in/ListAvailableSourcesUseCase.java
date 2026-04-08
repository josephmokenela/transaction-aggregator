package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.DataSourceId;

import java.util.List;

public interface ListAvailableSourcesUseCase {
    List<DataSourceId> listAvailableSources();
}
