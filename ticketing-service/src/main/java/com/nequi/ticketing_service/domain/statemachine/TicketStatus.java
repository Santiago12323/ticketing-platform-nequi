package com.nequi.ticketing_service.domain.statemachine;

public enum TicketStatus {

    AVAILABLE,
    RESERVED,
    PROCESSING_PAYMENT,
    SOLD,
    COMPLIMENTARY;

    public boolean isFinalState() {
        return this == SOLD || this == COMPLIMENTARY;
    }

    public boolean isAccountable() {
        return this == SOLD;
    }

    public boolean isHoldingInventory() {
        return this == RESERVED || this == PROCESSING_PAYMENT;
    }
}