package com.nequi.inventory.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

public record EventId(String value) {

    public EventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("EventId cannot be null or blank");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format for EventId");
        }
    }

    @JsonCreator
    public static EventId of(String value) {
        return new EventId(value);
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static EventId newId() {
        return new EventId(UUID.randomUUID().toString());
    }
}