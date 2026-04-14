package io.mokenela.transactionaggregator.adapter.out.persistence;

import io.mokenela.transactionaggregator.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionEntityMapperTest {

    private final TransactionEntityMapper mapper = new TransactionEntityMapper();

    private static final UUID   TX_ID       = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID   CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID   ACCOUNT_ID  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BigDecimal AMOUNT  = new BigDecimal("42.50");
    private static final String CURRENCY    = "GBP";
    private static final Instant OCCURRED   = Instant.parse("2024-06-15T10:00:00Z");

    // toDomain
    @Test
    void toDomain_shouldMapAllFieldsCorrectly() {
        var entity = buildEntity();

        Transaction domain = mapper.toDomain(entity);

        assertThat(domain.id().value()).isEqualTo(TX_ID);
        assertThat(domain.customerId().value()).isEqualTo(CUSTOMER_ID);
        assertThat(domain.accountId().value()).isEqualTo(ACCOUNT_ID);
        assertThat(domain.amount().amount()).isEqualByComparingTo(AMOUNT);
        assertThat(domain.amount().currency().getCurrencyCode()).isEqualTo(CURRENCY);
        assertThat(domain.type()).isEqualTo(TransactionType.DEBIT);
        assertThat(domain.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(domain.description()).isEqualTo("Coffee shop");
        assertThat(domain.category()).isEqualTo(TransactionCategory.FOOD_AND_DINING);
        assertThat(domain.merchantName()).isEqualTo("Costa Coffee");
        assertThat(domain.dataSourceId().value()).isEqualTo("MANUAL");
        assertThat(domain.occurredAt()).isEqualTo(OCCURRED);
    }

    @Test
    void toDomain_shouldHandleNullMerchantName() {
        var entity = buildEntity();
        entity.setMerchantName(null);

        Transaction domain = mapper.toDomain(entity);

        assertThat(domain.merchantName()).isNull();
    }

    // toEntity
    @Test
    void toEntity_shouldMapAllFieldsCorrectly() {
        var domain = buildDomain();

        TransactionEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo(TX_ID);
        assertThat(entity.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(entity.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(entity.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(entity.getCurrencyCode()).isEqualTo(CURRENCY);
        assertThat(entity.getType()).isEqualTo("DEBIT");
        assertThat(entity.getStatus()).isEqualTo("COMPLETED");
        assertThat(entity.getDescription()).isEqualTo("Coffee shop");
        assertThat(entity.getCategory()).isEqualTo("FOOD_AND_DINING");
        assertThat(entity.getMerchantName()).isEqualTo("Costa Coffee");
        assertThat(entity.getDataSourceId()).isEqualTo("MANUAL");
        assertThat(entity.getOccurredAt()).isEqualTo(OCCURRED);
    }

    @Test
    void toEntity_shouldStoreEnumsAsTheirName() {
        var domain = buildDomain();

        TransactionEntity entity = mapper.toEntity(domain);

        // Enums must be stored as their exact .name() string — any mismatch breaks
        // the database round-trip since TransactionType.valueOf(entity.getType()) is
        // how toDomain() recovers them.
        assertThat(entity.getType()).isEqualTo(TransactionType.DEBIT.name());
        assertThat(entity.getStatus()).isEqualTo(TransactionStatus.COMPLETED.name());
        assertThat(entity.getCategory()).isEqualTo(TransactionCategory.FOOD_AND_DINING.name());
    }

    // round-trip
    @Test
    void toDomain_toEntity_shouldBeSymmetric() {
        var domain   = buildDomain();
        var entity   = mapper.toEntity(domain);
        var restored = mapper.toDomain(entity);

        assertThat(restored.id()).isEqualTo(domain.id());
        assertThat(restored.customerId()).isEqualTo(domain.customerId());
        assertThat(restored.accountId()).isEqualTo(domain.accountId());
        assertThat(restored.amount().amount()).isEqualByComparingTo(domain.amount().amount());
        assertThat(restored.amount().currency()).isEqualTo(domain.amount().currency());
        assertThat(restored.type()).isEqualTo(domain.type());
        assertThat(restored.status()).isEqualTo(domain.status());
        assertThat(restored.description()).isEqualTo(domain.description());
        assertThat(restored.category()).isEqualTo(domain.category());
        assertThat(restored.merchantName()).isEqualTo(domain.merchantName());
        assertThat(restored.dataSourceId()).isEqualTo(domain.dataSourceId());
        assertThat(restored.occurredAt()).isEqualTo(domain.occurredAt());
    }

    // helpers
    private TransactionEntity buildEntity() {
        var entity = new TransactionEntity();
        entity.setId(TX_ID);
        entity.setCustomerId(CUSTOMER_ID);
        entity.setAccountId(ACCOUNT_ID);
        entity.setAmount(AMOUNT);
        entity.setCurrencyCode(CURRENCY);
        entity.setType("DEBIT");
        entity.setStatus("COMPLETED");
        entity.setDescription("Coffee shop");
        entity.setCategory("FOOD_AND_DINING");
        entity.setMerchantName("Costa Coffee");
        entity.setDataSourceId("MANUAL");
        entity.setOccurredAt(OCCURRED);
        return entity;
    }

    private Transaction buildDomain() {
        return new Transaction(
                new TransactionId(TX_ID),
                new CustomerId(CUSTOMER_ID),
                new AccountId(ACCOUNT_ID),
                Money.of(AMOUNT, CURRENCY),
                TransactionType.DEBIT,
                TransactionStatus.COMPLETED,
                "Coffee shop",
                TransactionCategory.FOOD_AND_DINING,
                "Costa Coffee",
                new DataSourceId("MANUAL"),
                OCCURRED
        );
    }
}
