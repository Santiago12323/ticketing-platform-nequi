package com.nequi.inventory.domain.statemachine;

public enum TicketStatus {
    AVAILABLE,
    RESERVED,
    PENDING_CONFIRMATION,
    SOLD,
    COMPLIMENTARY;

    public boolean isFinalState() {
        return this == SOLD || this == COMPLIMENTARY ;
    }
}