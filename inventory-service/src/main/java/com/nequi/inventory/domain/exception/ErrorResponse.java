package com.nequi.inventory.domain.exception;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        String errorCode,
        int status,
        String message,
        String path
) {
    public static ErrorResponse of(String errorCode, int status, String message, String path) {
        return new ErrorResponse(Instant.now(), errorCode, status, message, path);
    }
}
