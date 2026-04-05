package com.nequi.ticketing_service.domain.valueobject;

import com.nequi.ticketing_service.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.Set;

public record Money(BigDecimal amount, Currency currency) {

    private static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "COP");

    public Money {
        if (amount == null || currency == null) {
            throw new BusinessException("MONEY_NULL", "Amount and Currency cannot be null");
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("NEGATIVE_MONEY", "Amount cannot be negative");
        }

        if (!ALLOWED_CURRENCIES.contains(currency.getCurrencyCode())) {
            throw new BusinessException(
                    "UNSUPPORTED_CURRENCY",
                    "Currency %s is not supported. Only USD and COP are allowed.".formatted(currency.getCurrencyCode())
            );
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        try {
            return new Money(amount, Currency.getInstance(currencyCode));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_CURRENCY_CODE", "Currency code is invalid: " + currencyCode);
        }
    }

    public static Money of(double amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }

    public static Money zero(String currencyCode) {
        return of(BigDecimal.ZERO, currencyCode);
    }

    public static Money zeroCOP() {
        return zero("COP");
    }

    public static Money zeroUSD() {
        return zero("USD");
    }


    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);
        return new Money(result, this.currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new BusinessException("INVALID_QUANTITY", "Quantity cannot be negative");
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    private void validateSameCurrency(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        if (!this.currency.equals(other.currency)) {
            throw new BusinessException(
                    "CURRENCY_MISMATCH",
                    "Cannot operate on different currencies: %s and %s".formatted(this.currency, other.currency)
            );
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(amount, currency.getCurrencyCode());
    }
}