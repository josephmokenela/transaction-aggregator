package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.*;
import org.springframework.stereotype.Component;

/**
 * Bidirectional mapper between the {@link Transaction} domain record and the
 * {@link TransactionEntity} persistence entity.
 *
 * <p>Enum fields are stored as their {@link Enum#name()} string in the database;
 * {@link Money} is decomposed into {@code amount} + {@code currency_code} columns.</p>
 */
@Component
class TransactionEntityMapper {

    Transaction toDomain(TransactionEntity entity) {
        return new Transaction(
                new TransactionId(entity.getId()),
                new CustomerId(entity.getCustomerId()),
                new AccountId(entity.getAccountId()),
                Money.of(entity.getAmount(), entity.getCurrencyCode()),
                TransactionType.valueOf(entity.getType()),
                TransactionStatus.valueOf(entity.getStatus()),
                entity.getDescription(),
                TransactionCategory.valueOf(entity.getCategory()),
                entity.getMerchantName(),
                new DataSourceId(entity.getDataSourceId()),
                entity.getOccurredAt()
        );
    }

    TransactionEntity toEntity(Transaction domain) {
        var entity = new TransactionEntity();
        entity.setId(domain.id().value());
        entity.setCustomerId(domain.customerId().value());
        entity.setAccountId(domain.accountId().value());
        entity.setAmount(domain.amount().amount());
        entity.setCurrencyCode(domain.amount().currency().getCurrencyCode());
        entity.setType(domain.type().name());
        entity.setStatus(domain.status().name());
        entity.setDescription(domain.description());
        entity.setCategory(domain.category().name());
        entity.setMerchantName(domain.merchantName());
        entity.setDataSourceId(domain.dataSourceId().value());
        entity.setOccurredAt(domain.occurredAt());
        return entity;
    }
}
