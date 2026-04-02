package com.nequi.inventory.domain.statemachine;

public enum TicketEvent {
    RESERVE,
    START_PAYMENT,
    CONFIRM_PAYMENT,
    FAIL_PAYMENT,
    EXPIRE_RESERVATION,
    CANCEL_RESERVATION,
    ASSIGN_COMPLIMENTARY
}