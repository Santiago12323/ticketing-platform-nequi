package com.nequi.ticketing_service.domain.statemachine;

public enum OrderEvent {
    RESERVE_TICKET,
    START_PAYMENT,
    CONFIRM_PAYMENT,
    FAIL_PAYMENT,
    EXPIRE_RESERVATION,
    CANCEL,
    ASSIGN_COMPLIMENTARY
}