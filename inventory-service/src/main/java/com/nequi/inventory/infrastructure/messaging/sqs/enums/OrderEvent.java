package com.nequi.inventory.infrastructure.messaging.sqs.enums;

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