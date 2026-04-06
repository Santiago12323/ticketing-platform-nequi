package com.nequi.ticketing_service.application.infrastruture.messaging.redis.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.infrastructure.messaging.redis.payment.RedisPaymentProcessor;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisPaymentProcessorTest {

    @Mock
    private StreamReceiver<String, ObjectRecord<String, String>> streamReceiver;
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock
    private OrderPublisher publisher;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ReactiveStreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private RedisPaymentProcessor redisPaymentProcessor;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }



    @Test
    @DisplayName("AAA - Error: Should handle error during processing and return Mono.empty()")
    void processPaymentRecord_Error() throws Exception {
        // Arrange
        ObjectRecord<String, String> mockRecord = ObjectRecord.create(CacheConstants.PAYMENTS_STREAM, "bad-json")
                .withId(RecordId.autoGenerate());

        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("Parser Error"));

        // Act
        Mono<Void> result = (Mono<Void>) ReflectionTestUtils.invokeMethod(redisPaymentProcessor, "processPaymentRecord", mockRecord);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(publisher);
        verify(streamOperations, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    @DisplayName("AAA - Lifecycle: init should start consumption")
    void init_ShouldSubscribe() {
        // Arrange
        when(streamReceiver.receive(any(), any(StreamOffset.class))).thenReturn(Flux.empty());

        // Act
        redisPaymentProcessor.init();

        // Assert
        verify(streamReceiver).receive(any(), any(StreamOffset.class));
    }
}