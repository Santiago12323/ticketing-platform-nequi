package com.nequi.inventory.domain.valueobject;

import java.util.UUID;

/**
 * Value object representing a unique event identifier.
 */
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

    public static EventId newId() {
        return new EventId(UUID.randomUUID().toString());
    }

    public static EventId of(String value) {
        return new EventId(value);
    }

    @Override
    public String toString() {
        return "EventId[%s]".formatted(value);
    }
}
