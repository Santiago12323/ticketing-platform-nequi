package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.port.out.OrderHistoryRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderHistoryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderHistoryServiceImplTest {

    @Mock
    private OrderHistoryRepository repository;

    @InjectMocks
    private OrderHistoryServiceImpl orderHistoryService;

    private final OrderId VALID_ORDER_ID = OrderId.of(UUID.randomUUID().toString());

    @Test
    @DisplayName("Success: Should record timestamp when all parameters are provided")
    void recordTimestamp_FullSuccess() {
        // Arrange
        when(repository.saveHistory(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(orderHistoryService.recordTimestamp(
                        VALID_ORDER_ID,
                        OrderStatus.PENDING_PAYMENT,
                        OrderStatus.PAID,
                        "Payment verified"))
                .verifyComplete();

        verify(repository).saveHistory(
                eq(VALID_ORDER_ID.value()),
                eq("PENDING_PAYMENT"),
                eq("PAID"),
                eq("Payment verified")
        );
    }

    @Test
    @DisplayName("Success: Should use 'START' when 'from' status is null")
    void recordTimestamp_StartStatus() {
        // Arrange
        when(repository.saveHistory(anyString(), eq("START"), anyString(), anyString()))
                .thenReturn(Mono.empty());

        // Act
        StepVerifier.create(orderHistoryService.recordTimestamp(
                        VALID_ORDER_ID,
                        null,
                        OrderStatus.PENDING_VALIDATION,
                        "Initial ingestion"))
                .verifyComplete();

        verify(repository).saveHistory(anyString(), eq("START"), anyString(), anyString());
    }

    @Test
    @DisplayName("Success: Should use default details when 'details' is null or blank")
    void recordTimestamp_DefaultDetails() {
        // Arrange
        when(repository.saveHistory(anyString(), anyString(), anyString(), contains("No additional details")))
                .thenReturn(Mono.empty());

        // Act
        StepVerifier.create(orderHistoryService.recordTimestamp(
                        VALID_ORDER_ID,
                        OrderStatus.PENDING_PAYMENT,
                        OrderStatus.EXPIRED,
                        "   "))
                .verifyComplete();

        verify(repository).saveHistory(anyString(), anyString(), anyString(), eq("No additional details provided"));
    }

    @Test
    @DisplayName("Error: Should return error when OrderId or 'to' status is null")
    void recordTimestamp_ValidationErrors() {
        StepVerifier.create(orderHistoryService.recordTimestamp(null, null, OrderStatus.PAID, "test"))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(orderHistoryService.recordTimestamp(VALID_ORDER_ID, null, null, "test"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Success: Should return history flux from repository")
    void getHistory_Success() {
        // Arrange
        OrderHistoryEntity entity = new OrderHistoryEntity();
        entity.setOrderId(VALID_ORDER_ID.value());

        when(repository.findByOrderId(VALID_ORDER_ID.value()))
                .thenReturn(Flux.just(entity));

        // Act & Assert
        StepVerifier.create(orderHistoryService.getHistory(VALID_ORDER_ID))
                .expectNext(entity)
                .verifyComplete();

        verify(repository).findByOrderId(VALID_ORDER_ID.value());
    }
}