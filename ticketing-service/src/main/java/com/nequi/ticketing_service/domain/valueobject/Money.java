package com.nequi.ticketing_service.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value object representing money with currency.
 *
 * - Immutable
 * - Safe for financial operations
 * - Uses BigDecimal (no floating point errors)
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money of(double amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of(BigDecimal.ZERO, currencyCode);
    }

    public static Money zeroCOP() {
        return zero("COP");
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);

        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Resulting amount cannot be negative");
        }

        return new Money(result, this.currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        return new Money(
                this.amount.multiply(BigDecimal.valueOf(quantity)),
                this.currency
        );
    }

    /**
     * Compares this Money instance with another to determine if it is greater.
     *
     * <p>Both Money objects must have the same currency. If the currencies differ,
     * an {@link IllegalArgumentException} is thrown.</p>
     *
     * @param other the Money instance to compare against
     * @return {@code true} if this amount is greater than the other amount,
     *         {@code false} otherwise
     * @throws NullPointerException if {@code other} is null
     * @throws IllegalArgumentException if the currencies do not match
     */
    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Determines whether this Money instance has the same value as another.
     *
     * <p>Two Money objects are considered to have the same value if:
     * <ul>
     *   <li>Their amounts are numerically equal (ignoring scale differences, e.g., 10.0 == 10.00)</li>
     *   <li>Their currencies are equal</li>
     * </ul>
     * </p>
     *
     * @param other the Money instance to compare against
     * @return {@code true} if both amount and currency are equal, {@code false} otherwise
     */
    public boolean sameValue(Money other) {
        return this.amount.compareTo(other.amount) == 0
                && this.currency.equals(other.currency);
    }

    private void validateSameCurrency(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");

        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: %s and %s"
                            .formatted(this.currency, other.currency)
            );
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(amount, currency.getCurrencyCode());
    }
}