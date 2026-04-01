package com.nequi.ticketing_service.domain.statemachine;

public enum OrderEvent {
    VALIDATION_SUCCESS,
    VALIDATION_FAILED,
    START_PAYMENT,
    CONFIRM_PAYMENT,
    FAIL_PAYMENT,
    CANCEL,
    EXPIRE
}