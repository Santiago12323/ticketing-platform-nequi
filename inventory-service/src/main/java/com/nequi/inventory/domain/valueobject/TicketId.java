package com.nequi.inventory.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;

public record TicketId(String value) {

    public TicketId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TicketId cannot be null or blank");
        }
    }

    @JsonCreator
    public static TicketId of(@JsonProperty("value") String value) {
        return new TicketId(value);
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static TicketId generate() {
        return new TicketId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return "TicketId[%s]".formatted(value);
    }
}