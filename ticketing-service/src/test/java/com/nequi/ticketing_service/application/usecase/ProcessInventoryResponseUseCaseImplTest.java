package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderHistoryService;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessInventoryResponseUseCaseImplTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private CacheKeyGenerator keyGenerator;

    @Mock
    private OrderHistoryService historyService;

    @InjectMocks
    private ProcessInventoryResponseUseCaseImpl processInventoryResponseUseCase;

    private final String RAW_ID = "550e8400-e29b-41d4-a716-446655440000";
    private final OrderId VALID_ORDER_ID = OrderId.of(RAW_ID);
    private final String CACHE_KEY = "order:cache:" + RAW_ID;

    @Test
    @DisplayName("Should confirm order and delete cache when inventory is success")
    void executeSuccessPath() {
        // Arrange
        Order orderMock = mock(Order.class);
        when(orderMock.getStatus()).thenReturn(OrderStatus.PENDING_VALIDATION);

        when(repository.findById(VALID_ORDER_ID)).thenReturn(Mono.just(orderMock));
        when(repository.updateStatus(orderMock)).thenReturn(Mono.just(orderMock));

        when(historyService.recordTimestamp(any(), any(), any(), anyString()))
                .thenReturn(Mono.empty());

        when(keyGenerator.generateOrderKey(VALID_ORDER_ID.value())).thenReturn(CACHE_KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.delete(CACHE_KEY)).thenReturn(Mono.just(true));

        // Act
        Mono<Void> result = processInventoryResponseUseCase.execute(VALID_ORDER_ID, OrderEvent.VALIDATION_SUCCESS);

        // Assert
        StepVerifier.create(result).verifyComplete();

        verify(orderMock).confirmInventory();
        verify(repository).updateStatus(orderMock);
        verify(historyService).recordTimestamp(eq(VALID_ORDER_ID), any(), any(), anyString());
        verify(valueOperations).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("Should fail order and delete cache when inventory fails")
    void executeFailurePath() {
        // Arrange
        Order orderMock = mock(Order.class);
        when(orderMock.getStatus()).thenReturn(OrderStatus.PENDING_VALIDATION);

        when(repository.findById(VALID_ORDER_ID)).thenReturn(Mono.just(orderMock));
        when(repository.updateStatus(orderMock)).thenReturn(Mono.just(orderMock));

        when(historyService.recordTimestamp(any(), any(), any(), anyString()))
                .thenReturn(Mono.empty());

        when(keyGenerator.generateOrderKey(VALID_ORDER_ID.value())).thenReturn(CACHE_KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.delete(CACHE_KEY)).thenReturn(Mono.just(true));

        // Act
        Mono<Void> result = processInventoryResponseUseCase.execute(VALID_ORDER_ID, OrderEvent.VALIDATION_FAILED);

        // Assert
        StepVerifier.create(result).verifyComplete();

        verify(orderMock).failInventory();
        verify(repository).updateStatus(orderMock);
        verify(historyService).recordTimestamp(eq(VALID_ORDER_ID), any(), any(), anyString());
    }

    @Test
    @DisplayName("Should propagate error and NOT delete cache if DB update fails")
    void shouldFailWhenDbUpdateFails() {
        // Arrange
        Order orderMock = mock(Order.class);
        when(repository.findById(VALID_ORDER_ID)).thenReturn(Mono.just(orderMock));
        when(repository.updateStatus(any())).thenReturn(Mono.error(new RuntimeException("DynamoDB Down")));

        // Act
        Mono<Void> result = processInventoryResponseUseCase.execute(VALID_ORDER_ID, OrderEvent.VALIDATION_SUCCESS);

        // Assert
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(orderMock).confirmInventory();
        verify(valueOperations, never()).delete(anyString());
    }


}