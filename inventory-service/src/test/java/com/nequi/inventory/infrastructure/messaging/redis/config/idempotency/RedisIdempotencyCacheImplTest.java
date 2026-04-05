package com.nequi.inventory.infrastructure.messaging.redis.config.idempotency;

import com.nequi.inventory.domain.valueobject.OrderId;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyCacheImplTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisIdempotencyCacheImpl redisIdempotency;

    private OrderId orderId;
    private final String ORDER_ID_VALUE = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        orderId = new OrderId(ORDER_ID_VALUE);
    }

    @Test
    @DisplayName("Should return true when orderId exists in Redis")
    void exists_WhenKeyExists_ReturnsTrue() {
        // Arrange
        when(redisTemplate.hasKey(ORDER_ID_VALUE)).thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(redisIdempotency.exists(orderId))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).hasKey(ORDER_ID_VALUE);
    }

    @Test
    @DisplayName("Should return false when orderId does not exist in Redis")
    void exists_WhenKeyDoesNotExist_ReturnsFalse() {
        // Arrange
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(redisIdempotency.exists(orderId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should save orderId in Redis with 10 minutes TTL")
    void save_ShouldStoreKeyWithTTL() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(ORDER_ID_VALUE), anyString(), eq(Duration.ofMinutes(10))))
                .thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(redisIdempotency.save(orderId))
                .verifyComplete();

        // Assert (Verification)
        verify(valueOperations).set(ORDER_ID_VALUE, "1", Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("Should propagate error when Redis fails on save")
    void save_WhenRedisFails_ReturnsError() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis connection error")));

        // Act & Assert
        StepVerifier.create(redisIdempotency.save(orderId))
                .expectError(RuntimeException.class)
                .verify();
    }
}