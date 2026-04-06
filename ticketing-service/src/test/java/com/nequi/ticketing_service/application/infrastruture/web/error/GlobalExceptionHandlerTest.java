package com.nequi.ticketing_service.application.infrastruture.web.error;

import com.nequi.ticketing_service.domain.exception.BusinessException;
import com.nequi.ticketing_service.domain.exception.ErrorResponse;
import com.nequi.ticketing_service.infrastructure.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    private final String MOCK_PATH = "/api/v1/orders/confirm";

    @BeforeEach
    void setUp() {
        RequestPath requestPath = mock(RequestPath.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn(MOCK_PATH);
    }

    @Test
    @DisplayName("AAA - Error: Should handle BusinessException and return 409 Conflict")
    void handleBusiness_ShouldReturnConflict() {
        // Arrange
        BusinessException ex = new BusinessException("BUS-002", "Fondos insuficientes");

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleBusiness(ex, exchange);

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BUS-002", response.getBody().errorCode());
        assertEquals(MOCK_PATH, response.getBody().path());
        assertEquals("Fondos insuficientes", response.getBody().message());
    }

    @Test
    @DisplayName("AAA - Error: Should handle IllegalArgumentException and return 400 Bad Request")
    void handleValidation_ShouldReturnBadRequest() {
        // Arrange
        IllegalArgumentException ex = new IllegalArgumentException("Dato inválido");

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidation(ex, exchange);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VAL-001", response.getBody().errorCode());
        assertEquals(400, response.getBody().status());
    }

    @Test
    @DisplayName("AAA - Error: Should handle RuntimeException and return 500 Internal Server Error")
    void handleRuntime_ShouldReturnInternalError() {
        // Arrange
        RuntimeException ex = new RuntimeException("Crash");

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleRuntime(ex, exchange);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GEN-001", response.getBody().errorCode());
        assertEquals(500, response.getBody().status());
        assertNotNull(response.getBody().timestamp());
    }
}