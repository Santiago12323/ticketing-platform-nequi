package com.nequi.inventory.domain.valueobject;

import java.util.UUID;

public record TicketId(String value) {

    public TicketId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TicketId cannot be null or blank");
        }
    }

    public static TicketId of(String value) {
        return new TicketId(value);
    }

    public static TicketId generate() {
        return new TicketId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return "TicketId[%s]".formatted(value);
    }
}