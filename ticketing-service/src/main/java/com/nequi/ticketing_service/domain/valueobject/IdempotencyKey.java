package com.nequi.ticketing_service.domain.valueobject;

import java.util.UUID;

/**
 * Value object representing an idempotency key.
 *
 * Prevents duplicate order creation in distributed systems.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey cannot be null or blank");
        }
    }

    public static IdempotencyKey newKey() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    @Override
    public String toString() {
        return "IdempotencyKey[%s]".formatted(value);
    }
}