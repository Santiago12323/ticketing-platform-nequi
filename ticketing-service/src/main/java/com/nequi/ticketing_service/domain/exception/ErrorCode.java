package com.nequi.ticketing_service.domain.exception;

public enum ErrorCode {
    ORDER_EVENT_NOT_ACCEPTED("ORD-001"),
    ORDER_NOT_FOUND("ORD-002"),
    PAYMENT_FAILED("ORD-003"),
    INVALID_ORDER_STATE("ORD-004");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
