package com.nequi.ticketing_service.application.infrastruture.messaging.redis.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.UserId;
import com.nequi.ticketing_service.infrastructure.messaging.redis.payment.RedisPaymentIngestor;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheConstants;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisPaymentIngestorTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveStreamOperations<String, Object, Object> streamOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RedisPaymentIngestor redisPaymentIngestor;

    private UserId userId;
    private EventId eventId;
    private OrderId orderId;
    private List<String> seatIds;

    @BeforeEach
    void setUp() {
        userId = UserId.of(UUID.randomUUID().toString());
        eventId = EventId.of(UUID.randomUUID().toString());
        orderId = OrderId.of(UUID.randomUUID().toString());
        seatIds = List.of("A1", "A2");
    }

    @Test
    @DisplayName("AAA - Success: Should ingest payment intent into Redis Stream")
    void ingest_Success() {
        // Arrange
        ObjectNode mockNode = new ObjectMapper().createObjectNode();

        when(objectMapper.createObjectNode()).thenReturn(mockNode);
        when(redisTemplate.opsForStream()).thenReturn((ReactiveStreamOperations) streamOperations);
        when(streamOperations.add(any(ObjectRecord.class)))
                .thenReturn(Mono.just(RecordId.autoGenerate()));

        // Act
        Mono<Void> result = redisPaymentIngestor.ingest(userId, eventId, seatIds, orderId);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<ObjectRecord<String, String>> captor = ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOperations).add(captor.capture());

        ObjectRecord<String, String> capturedRecord = captor.getValue();

        assertEquals(CacheConstants.PAYMENTS_STREAM, capturedRecord.getStream());
        String jsonValue = capturedRecord.getValue();

        assertTrue(jsonValue.contains(orderId.value()));
        assertTrue(jsonValue.contains(userId.value()));
        assertTrue(jsonValue.contains("CONFIRM_PAYMENT"));
        assertTrue(jsonValue.contains("PAY-"));
    }

    @Test
    @DisplayName("AAA - Error: Should fail when JSON processing throws exception")
    void ingest_JsonError() {
        // Arrange
        when(objectMapper.createObjectNode()).thenThrow(new RuntimeException("JSON Creation Error"));

        // Act
        Mono<Void> result = redisPaymentIngestor.ingest(userId, eventId, seatIds, orderId);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("JSON Creation Error"))
                .verify();
        verifyNoInteractions(redisTemplate);
    }
}