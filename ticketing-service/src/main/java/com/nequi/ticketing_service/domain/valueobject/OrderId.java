package com.nequi.ticketing_service.domain.valueobject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

/**
 * Value object representing a unique order identifier.
 */
public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be null or blank");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format for OrderId");
        }
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID().toString());
    }

    @JsonCreator
    public static OrderId of(String value) {
        return new OrderId(value);
    }

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "OrderId[%s]".formatted(value);
    }
}