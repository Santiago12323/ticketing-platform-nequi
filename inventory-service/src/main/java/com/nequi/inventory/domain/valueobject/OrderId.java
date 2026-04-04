package com.nequi.inventory.domain.valueobject;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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

    @JsonCreator
    public static OrderId of(String value) {
        return new OrderId(value);
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID().toString());
    }
}