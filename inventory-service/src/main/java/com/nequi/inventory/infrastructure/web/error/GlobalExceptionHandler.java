package com.nequi.inventory.infrastructure.web.error;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, ServerWebExchange exchange) {
        return buildResponse(ex.getErrorCode(), HttpStatus.CONFLICT, ex.getMessage(), exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException ex, ServerWebExchange exchange) {
        return buildResponse("VAL-001", HttpStatus.BAD_REQUEST, ex.getMessage(), exchange);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex, ServerWebExchange exchange) {
        return buildResponse("GEN-001", HttpStatus.INTERNAL_SERVER_ERROR, "Internal system error", exchange);
    }

    private ResponseEntity<ErrorResponse> buildResponse(String code, HttpStatus status, String msg, ServerWebExchange ex) {
        ErrorResponse error = ErrorResponse.of(
                code,
                status.value(),
                msg,
                ex.getRequest().getPath().value()
        );
        return ResponseEntity.status(status).body(error);
    }
}