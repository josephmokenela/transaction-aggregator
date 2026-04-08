package io.mokenela.transactionaggregator.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Immutable value object representing a monetary amount in a specific currency.
 *
 * <p>The amount is always normalized to the currency's standard fraction digits
 * using {@link RoundingMode#HALF_EVEN} (banker's rounding) — the standard for
 * financial calculations, as it minimises cumulative rounding error.</p>
 *
 * <p>Mixed-currency arithmetic is rejected at the boundary; callers are responsible
 * for currency conversion before operating on {@code Money} values.</p>
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) throw new IllegalArgumentException("amount cannot be null");
        if (currency == null) throw new IllegalArgumentException("currency cannot be null");
        // Normalize to the currency's standard fraction digits (e.g. 2 for GBP/USD, 0 for JPY)
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, Currency.getInstance(currencyCode));
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: %s and %s".formatted(this.currency, other.currency));
        }
    }
}
