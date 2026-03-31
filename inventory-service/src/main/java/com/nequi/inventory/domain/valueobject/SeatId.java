package com.nequi.inventory.domain.valueobject;

/**
 * Value object representing a unique seat identifier.
 */
public record SeatId(String value) {

    public SeatId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SeatId cannot be null or blank");
        }
    }

    public static SeatId of(String value) {
        return new SeatId(value);
    }

    @Override
    public String toString() {
        return "SeatId[%s]".formatted(value);
    }
}
