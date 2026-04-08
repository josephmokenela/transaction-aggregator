package io.mokenela.transactionaggregator.domain.model;

public record DataSourceId(String value) {

    public static final DataSourceId MANUAL = new DataSourceId("MANUAL");
    public static final DataSourceId MOCK_BANK = new DataSourceId("MOCK_BANK");
    public static final DataSourceId MOCK_CARD = new DataSourceId("MOCK_CARD");
    public static final DataSourceId MOCK_PAYMENT_PROVIDER = new DataSourceId("MOCK_PAYMENT_PROVIDER");

    public DataSourceId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("DataSourceId cannot be blank");
    }
}
