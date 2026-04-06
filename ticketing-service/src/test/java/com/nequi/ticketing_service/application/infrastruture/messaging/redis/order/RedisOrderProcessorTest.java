package com.nequi.ticketing_service.application.infrastruture.messaging.redis.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.infrastructure.messaging.redis.Order.RedisOrderProcessor;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisOrderProcessorTest {

    @Mock
    private StreamReceiver<String, ObjectRecord<String, String>> streamReceiver;
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock
    private OrderRepository repository;
    @Mock
    private OrderPublisher publisher;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ReactiveStreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private RedisOrderProcessor redisOrderProcessor;

    private final String STREAM_KEY = "orders-stream";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisOrderProcessor, "streamKey", STREAM_KEY);
        ReflectionTestUtils.setField(redisOrderProcessor, "auditEnabled", true);

        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    @DisplayName("AAA - Success: Should process record, save to DB, publish and ACK")
    void processRecord_Success() throws Exception {
        // Arrange
        String orderUuid = UUID.randomUUID().toString();
        String jsonValue = "{\"orderId\":\"" + orderUuid + "\"}";

        ObjectRecord<String, String> mockRecord = ObjectRecord.create(STREAM_KEY, jsonValue)
                .withId(RecordId.autoGenerate());

        JsonNode mockNode = mock(JsonNode.class);
        when(objectMapper.readTree(anyString())).thenReturn(mockNode);
        when(mockNode.get("orderId")).thenReturn(mock(JsonNode.class));
        when(mockNode.get("orderId").asText()).thenReturn(orderUuid);
        when(mockNode.get("userId")).thenReturn(mock(JsonNode.class));
        when(mockNode.get("userId").asText()).thenReturn(UUID.randomUUID().toString());
        when(mockNode.get("eventId")).thenReturn(mock(JsonNode.class));
        when(mockNode.get("eventId").asText()).thenReturn(UUID.randomUUID().toString());
        when(mockNode.get("totalPrice")).thenReturn(mock(JsonNode.class));
        when(mockNode.get("totalPrice").asDouble()).thenReturn(50000.0);
        when(mockNode.get("currency")).thenReturn(mock(JsonNode.class));
        when(mockNode.get("currency").asText()).thenReturn("COP");
        when(mockNode.get("seatIds")).thenReturn(null); // Para cubrir rama de mapSeats vacío

        when(repository.save(any(Order.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(publisher.publishInventoryCheck(any(), any(), any())).thenReturn(Mono.empty());

        when(streamOperations.acknowledge(anyString(), anyString(), any(RecordId.class)))
                .thenReturn(Mono.just(1L));

        when(streamReceiver.receive(any(), any(StreamOffset.class))).thenReturn(Flux.just(mockRecord));

        redisOrderProcessor.init();

        verify(repository, timeout(1000)).save(any());
        verify(publisher, timeout(1000)).publishInventoryCheck(any(), any(), any());
        verify(streamOperations, timeout(1000)).acknowledge(eq(STREAM_KEY), anyString(), eq(mockRecord.getId()));
    }

    @Test
    @DisplayName("AAA - Error: Should catch error during processing and continue (onErrorResume)")
    void processRecord_ErrorHandled() throws Exception {
        // Arrange
        ObjectRecord<String, String> mockRecord = ObjectRecord.create(STREAM_KEY, "invalid-json")
                .withId(RecordId.autoGenerate());

        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("JSON Malformed"));

        when(streamOperations.acknowledge(anyString(), anyString(), any(RecordId.class)))
                .thenReturn(Mono.just(1L));

        // Act
        Mono<Void> result = (Mono<Void>) ReflectionTestUtils.invokeMethod(redisOrderProcessor, "processRecord", mockRecord);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(repository);
        verifyNoInteractions(publisher);

        verify(streamOperations).acknowledge(eq(STREAM_KEY), anyString(), eq(mockRecord.getId()));
    }

    @Test
    @DisplayName("AAA - Logic: mapSeats should return empty list when node is null")
    void mapSeats_NullNode_ReturnsEmptyList() {
        // Act
        List<?> result = (List<?>) ReflectionTestUtils.invokeMethod(redisOrderProcessor, "mapSeats", (Object) null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}