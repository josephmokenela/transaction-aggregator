package io.mokenela.transactionaggregator.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldNormalizeScaleToTwoDecimalPlaces_forGBP() {
        var money = Money.of(new BigDecimal("10.5"), "GBP");
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("10.50"));
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void shouldNormalizeScaleToZeroDecimalPlaces_forJPY() {
        var money = Money.of(new BigDecimal("1500.7"), "JPY");
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("1501"));
        assertThat(money.amount().scale()).isEqualTo(0);
    }

    @Test
    void shouldAddAmountsAndPreserveScale() {
        var a = Money.of(new BigDecimal("10.25"), "GBP");
        var b = Money.of(new BigDecimal("5.75"), "GBP");

        var result = a.add(b);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("16.00"));
        assertThat(result.currency().getCurrencyCode()).isEqualTo("GBP");
    }

    @Test
    void shouldSubtractAmounts() {
        var a = Money.of(new BigDecimal("100.00"), "GBP");
        var b = Money.of(new BigDecimal("35.50"), "GBP");

        var result = a.subtract(b);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("64.50"));
    }

    @Test
    void shouldNegateAmount() {
        var money = Money.of(new BigDecimal("42.00"), "GBP");
        assertThat(money.negate().amount()).isEqualByComparingTo(new BigDecimal("-42.00"));
    }

    @Test
    void shouldCreateZeroMoneyWithCorrectScale() {
        var zero = Money.zero("GBP");
        assertThat(zero.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(zero.amount().scale()).isEqualTo(2);
    }

    @Test
    void shouldClassifySignCorrectly() {
        assertThat(Money.of(new BigDecimal("1.00"), "GBP").isPositive()).isTrue();
        assertThat(Money.of(new BigDecimal("-1.00"), "GBP").isNegative()).isTrue();
        assertThat(Money.zero("GBP").isZero()).isTrue();
        assertThat(Money.zero("GBP").isPositive()).isFalse();
        assertThat(Money.zero("GBP").isNegative()).isFalse();
    }

    @Test
    void shouldThrowWhenAddingDifferentCurrencies() {
        var gbp = Money.of(new BigDecimal("10.00"), "GBP");
        var usd = Money.of(new BigDecimal("10.00"), "USD");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> gbp.add(usd))
                .withMessageContaining("GBP")
                .withMessageContaining("USD");
    }

    @Test
    void shouldThrowWhenSubtractingDifferentCurrencies() {
        var gbp = Money.of(new BigDecimal("10.00"), "GBP");
        var eur = Money.of(new BigDecimal("10.00"), "EUR");

        assertThatIllegalArgumentException().isThrownBy(() -> gbp.subtract(eur));
    }

    @Test
    void shouldThrowOnNullAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(null, java.util.Currency.getInstance("GBP")));
    }

    @Test
    void shouldThrowOnNullCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.ONE, null));
    }

    @Test
    void reduceShouldProduceCorrectTotal_whenStreamingOverMultipleAmounts() {
        var zero = Money.zero("GBP");
        var total = java.util.List.of(
                Money.of(new BigDecimal("10.00"), "GBP"),
                Money.of(new BigDecimal("20.50"), "GBP"),
                Money.of(new BigDecimal("5.25"), "GBP")
        ).stream().reduce(zero, Money::add);

        assertThat(total.amount()).isEqualByComparingTo(new BigDecimal("35.75"));
    }
}
