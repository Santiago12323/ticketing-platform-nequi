package com.nequi.ticketing_service.application.infrastruture.messaging.redis.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.messaging.redis.Order.RedisOrderIngestorImpl;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisOrderIngestorImplTest {

    @Mock private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock private ReactiveStreamOperations<String, Object, Object> streamOperations;
    @Mock private ReactiveValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper objectMapper;
    @Mock private CacheKeyGenerator keyGenerator;

    @InjectMocks
    private RedisOrderIngestorImpl redisOrderIngestor;

    private final UserId userId = UserId.of(UUID.randomUUID().toString());
    private final EventId eventId = EventId.of(UUID.randomUUID().toString());
    private final OrderId orderId = OrderId.of(UUID.randomUUID().toString());
    private final Money money = new Money(BigDecimal.valueOf(100), Currency.getInstance("COP"));
    private final List<String> seats = List.of("A1");
    private final String streamKey = "orders-stream";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisOrderIngestor, "streamKey", streamKey);
    }

    @Test
    @DisplayName("AAA - Success: Should ingest order into stream and set cache")
    void ingest_Success() throws JsonProcessingException {
        // Arrange
        String jsonPayload = "{\"orderId\":\"test\"}";
        String cacheKey = "order:test";

        when(objectMapper.writeValueAsString(anyMap())).thenReturn(jsonPayload);
        when(keyGenerator.generateOrderKey(orderId.value())).thenReturn(cacheKey);

        when(redisTemplate.opsForStream()).thenReturn((ReactiveStreamOperations) streamOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(streamOperations.add(any(ObjectRecord.class))).thenReturn(Mono.just(RecordId.autoGenerate()));
        when(valueOperations.set(eq(cacheKey), eq(jsonPayload), any())).thenReturn(Mono.just(true));

        // Act
        Mono<OrderId> result = redisOrderIngestor.ingest(userId, eventId, money, seats, orderId);

        // Assert
        StepVerifier.create(result)
                .expectNext(orderId)
                .verifyComplete();

        verify(valueOperations).set(eq(cacheKey), eq(jsonPayload), any());
        ArgumentCaptor<ObjectRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOperations).add(recordCaptor.capture());

        ObjectRecord<String, String> capturedRecord = recordCaptor.getValue();

        assertEquals(streamKey, capturedRecord.getStream(), "El nombre del Stream debe coincidir");
        assertEquals(jsonPayload, capturedRecord.getValue(), "El JSON enviado al Stream debe coincidir");
    }
    @Test
    @DisplayName("AAA - Error: Should fail when JSON serialization fails")
    void ingest_SerializationError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(anyMap()))
                .thenThrow(new RuntimeException("Jackson failure"));

        // Act
        Mono<OrderId> result = redisOrderIngestor.ingest(userId, eventId, money, seats, orderId);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("Jackson failure"))
                .verify();

        verifyNoInteractions(redisTemplate);
    }
}