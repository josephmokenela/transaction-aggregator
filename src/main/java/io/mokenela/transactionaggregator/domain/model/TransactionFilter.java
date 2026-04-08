package io.mokenela.transactionaggregator.domain.model;

import java.time.Instant;

/**
 * Flexible filter for transaction queries. Null fields are treated as "no filter on this dimension".
 */
public record TransactionFilter(
        CustomerId customerId,
        AccountId accountId,
        TransactionCategory category,
        TransactionType type,
        DataSourceId dataSourceId,
        String keyword,
        Instant from,
        Instant to
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CustomerId customerId;
        private AccountId accountId;
        private TransactionCategory category;
        private TransactionType type;
        private DataSourceId dataSourceId;
        private String keyword;
        private Instant from;
        private Instant to;

        public Builder customerId(CustomerId customerId) { this.customerId = customerId; return this; }
        public Builder accountId(AccountId accountId) { this.accountId = accountId; return this; }
        public Builder category(TransactionCategory category) { this.category = category; return this; }
        public Builder type(TransactionType type) { this.type = type; return this; }
        public Builder dataSourceId(DataSourceId dataSourceId) { this.dataSourceId = dataSourceId; return this; }
        public Builder keyword(String keyword) { this.keyword = keyword; return this; }
        public Builder from(Instant from) { this.from = from; return this; }
        public Builder to(Instant to) { this.to = to; return this; }

        public TransactionFilter build() {
            return new TransactionFilter(customerId, accountId, category, type, dataSourceId, keyword, from, to);
        }
    }
}
