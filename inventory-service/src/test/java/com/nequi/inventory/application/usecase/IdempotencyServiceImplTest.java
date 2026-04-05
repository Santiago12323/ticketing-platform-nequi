package com.nequi.inventory.application.usecase;

import com.nequi.inventory.aplication.usecase.IdempotencyServiceImpl;
import com.nequi.inventory.domain.port.out.IdempotencyRepository;
import com.nequi.inventory.domain.port.out.RedisIdempotency;
import com.nequi.inventory.domain.valueobject.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    @Mock
    private RedisIdempotency redis;

    @Mock
    private IdempotencyRepository dynamo;

    @InjectMocks
    private IdempotencyServiceImpl idempotencyService;

    private OrderId orderId;

    @BeforeEach
    void setUp() {
        orderId = new OrderId(UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("Should return false when key already exists in Redis")
    void tryProcess_AlreadyInRedis() {
        // Arrange
        when(redis.exists(orderId)).thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(idempotencyService.tryProcess(orderId))
                .expectNext(false)
                .verifyComplete();

        verify(dynamo, never()).saveIfNotExists(any());
        verify(redis, never()).save(any());
    }

    @Test
    @DisplayName("Should return false when key exists in Dynamo but not in Redis")
    void tryProcess_ExistsInDynamoOnly() {
        // Arrange
        when(redis.exists(orderId)).thenReturn(Mono.just(false));
        when(dynamo.saveIfNotExists(orderId)).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(idempotencyService.tryProcess(orderId))
                .expectNext(false)
                .verifyComplete();

        verify(redis, never()).save(any());
    }

    @Test
    @DisplayName("Should return true and update Redis when key is new in both providers")
    void tryProcess_NewKeySuccess() {
        // Arrange
        when(redis.exists(orderId)).thenReturn(Mono.just(false));
        when(dynamo.saveIfNotExists(orderId)).thenReturn(Mono.just(true));
        when(redis.save(orderId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(idempotencyService.tryProcess(orderId))
                .expectNext(true)
                .verifyComplete();
        verify(redis).exists(orderId);
        verify(dynamo).saveIfNotExists(orderId);
        verify(redis).save(orderId);
    }
}