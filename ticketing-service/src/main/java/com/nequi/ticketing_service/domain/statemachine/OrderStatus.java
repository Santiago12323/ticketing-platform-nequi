package com.nequi.ticketing_service.domain.statemachine;

public enum OrderStatus {
    PENDING_VALIDATION,
    PENDING_PAYMENT,
    PAID,
    CANCELLED,          // Cancelada por el usuario o sistema
    EXPIRED,
    FAILED_VALIDATION;

    public boolean isFinalState() {
        return this == PAID || this == CANCELLED || this == EXPIRED || this == FAILED_VALIDATION;
    }
}