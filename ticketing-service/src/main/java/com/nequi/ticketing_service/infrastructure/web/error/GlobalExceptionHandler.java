package com.nequi.ticketing_service.infrastructure.web.error;


import com.nequi.ticketing_service.domain.exception.BusinessException;
import com.nequi.ticketing_service.domain.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, ServerWebExchange exchange) {
        ErrorResponse error = ErrorResponse.of(
                ex.getErrorCode(),
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex, ServerWebExchange exchange) {
        ErrorResponse error = ErrorResponse.of(
                "GEN-001",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
