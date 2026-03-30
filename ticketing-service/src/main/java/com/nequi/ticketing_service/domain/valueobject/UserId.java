package com.nequi.ticketing_service.domain.valueobject;

/**
 * Value object representing a unique user identifier.
 * Comes from external authentication system.
 */
public record UserId(String value) {

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be null or blank");
        }
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return "UserId[%s]".formatted(value);
    }
}