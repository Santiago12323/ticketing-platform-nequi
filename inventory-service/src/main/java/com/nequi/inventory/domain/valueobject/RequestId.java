package com.nequi.inventory.domain.valueobject;

import java.util.UUID;

/**
 * Value object representing a unique request identifier for idempotency.
 */
public record RequestId(String value) {

    public RequestId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RequestId cannot be null or blank");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format for RequestId");
        }
    }

    public static RequestId newId() {
        return new RequestId(UUID.randomUUID().toString());
    }

    public static RequestId of(String value) {
        return new RequestId(value);
    }

    @Override
    public String toString() {
        return "RequestId[%s]".formatted(value);
    }
}
