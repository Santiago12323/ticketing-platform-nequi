package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
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

import java.util.UUID;

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

    @InjectMocks
    private ProcessInventoryResponseUseCaseImpl processInventoryResponseUseCase;

    private final OrderId VALID_ORDER_ID = OrderId.newId();
    private final String CACHE_KEY = "order:cache:" + VALID_ORDER_ID;

    @Test
    @DisplayName("Should confirm order and delete cache when inventory is success")
    void executeSuccessPath() {
        // Arrange
        Order orderMock = mock(Order.class);
        when(repository.findById(any(OrderId.class))).thenReturn(Mono.just(orderMock));

        when(orderMock.confirmInventory()).thenReturn(Mono.just(orderMock));
        when(repository.updateStatus(orderMock)).thenReturn(Mono.just(orderMock));

        // Mocking Redis
        when(keyGenerator.generateOrderKey(VALID_ORDER_ID.value())).thenReturn(CACHE_KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.delete(CACHE_KEY)).thenReturn(Mono.just(true));

        // Act
        Mono<Void> result = processInventoryResponseUseCase.execute(VALID_ORDER_ID, true);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(orderMock).confirmInventory();
        verify(repository).updateStatus(orderMock);
        verify(valueOperations).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("Should cancel order and delete cache when inventory fails")
    void executeFailurePath() {
        // Arrange
        Order orderMock = mock(Order.class);
        when(repository.findById(any(OrderId.class))).thenReturn(Mono.just(orderMock));

        when(orderMock.cancel()).thenReturn(Mono.just(orderMock));
        when(repository.updateStatus(orderMock)).thenReturn(Mono.just(orderMock));

        when(keyGenerator.generateOrderKey(VALID_ORDER_ID.value())).thenReturn(CACHE_KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.delete(CACHE_KEY)).thenReturn(Mono.just(true));

        // Act
        Mono<Void> result = processInventoryResponseUseCase.execute(VALID_ORDER_ID, false);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(orderMock).cancel();
        verify(orderMock, never()).confirmInventory();
        verify(repository).updateStatus(orderMock);
    }

    @Test
    @DisplayName("Should propagate error and NOT delete cache if DB update fails")
    void shouldFailWhenDbUpdateFails() {
        // Arrange
        Order orderMock = mock(Order.class);
        when(repository.findById(any(OrderId.class))).thenReturn(Mono.just(orderMock));
        when(orderMock.confirmInventory()).thenReturn(Mono.just(orderMock));

        when(repository.updateStatus(any())).thenReturn(Mono.error(new RuntimeException("DynamoDB Down")));

        // Act
        Mono<Void> result = processInventoryResponseUseCase.execute(VALID_ORDER_ID, true);

        // Assert
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(valueOperations, never()).delete(anyString());
    }
}