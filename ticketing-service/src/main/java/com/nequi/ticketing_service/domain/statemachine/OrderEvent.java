package com.nequi.ticketing_service.domain.statemachine;

public enum OrderEvent {
    START_PROCESS,
    VALIDATION_SUCCESS,
    VALIDATION_FAILED,
    START_PAYMENT,
    CONFIRM_PAYMENT,
    FAIL_PAYMENT,
    CANCEL,
    EXPIRE
}