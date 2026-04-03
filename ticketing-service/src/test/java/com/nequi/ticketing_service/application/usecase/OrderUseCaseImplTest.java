package com.nequi.ticketing_service.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.port.out.RedisOrderIngestor;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import com.nequi.ticketing_service.infrastructure.persistence.factory.OrderFactory;
import com.nequi.ticketing_service.infrastructure.persistence.mapper.OrderEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderUseCaseImplTest {

    @Mock private RedisOrderIngestor redisOrderIngestor;
    @Mock private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;
    @Mock private OrderRepository repository;
    @Mock private OrderFactory orderFactory;
    @Mock private ObjectMapper objectMapper;
    @Mock private OrderEntityMapper mapper;

    @InjectMocks
    private OrderUseCaseImpl orderUseCase;

    private final UserId userId = UserId.of(UUID.randomUUID().toString());
    private final EventId eventId = EventId.of(UUID.randomUUID().toString());
    private final Money price = new Money(BigDecimal.valueOf(150000), Currency.getInstance("COP"));
    private final List<String> seats = List.of("A1", "A2");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderUseCase, "auditEnabled", true);
        ReflectionTestUtils.setField(orderUseCase, "cacheTtl", 10L);
    }

    @Test
    @DisplayName("Should create order intent and return orderId")
    void createOrderSuccessfully() {
        // Arrange
        when(redisOrderIngestor.ingest(eq(userId), eq(eventId), eq(price), eq(seats), any(OrderId.class)))
                .thenReturn(Mono.just(OrderId.of(UUID.randomUUID().toString())));

        // Act
        Mono<OrderId> result = orderUseCase.create(userId, eventId, price, seats);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(id -> id.value() != null)
                .verifyComplete();

        verify(redisOrderIngestor).ingest(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should return order from cache if present")
    void getByIdFromCacheSuccessfully() throws JsonProcessingException {
        // Arrange
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        String cacheKey = "order:cache:" + orderId.value();
        String jsonMock = "{\"id\":\"" + orderId.value() + "\"}";
        OrderEntity entityMock = new OrderEntity();
        Order orderMock = mock(Order.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(Mono.just(jsonMock));

        when(objectMapper.readValue(eq(jsonMock), eq(OrderEntity.class))).thenReturn(entityMock);
        when(orderFactory.fromEntity(entityMock)).thenReturn(Mono.just(orderMock));

        // Act
        Mono<Order> result = orderUseCase.getById(orderId);

        // Assert
        StepVerifier.create(result)
                .expectNext(orderMock)
                .verifyComplete();

        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("Should fetch from DB and save to cache when cache is empty")
    void getByIdFromDbWhenCacheEmpty() throws JsonProcessingException {
        // Arrange
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        String cacheKey = "order:cache:" + orderId.value();
        Order orderMock = mock(Order.class);
        OrderEntity entityMock = new OrderEntity();

        when(orderMock.getId()).thenReturn(orderId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(cacheKey)).thenReturn(Mono.empty());
        when(repository.findById(orderId)).thenReturn(Mono.just(orderMock));

        when(mapper.toEntity(eq(orderMock), anyList())).thenReturn(entityMock);
        when(objectMapper.writeValueAsString(entityMock)).thenReturn("{}");
        when(valueOperations.set(eq(cacheKey), anyString(), any())).thenReturn(Mono.just(true));

        // Act
        Mono<Order> result = orderUseCase.getById(orderId);

        // Assert
        StepVerifier.create(result)
                .expectNext(orderMock)
                .verifyComplete();

        verify(repository).findById(orderId);
        verify(valueOperations).set(eq(cacheKey), anyString(), any());
    }

    @Test
    @DisplayName("Should fallback to DB if cache deserialization fails")
    void fallbackToDbOnCacheError() throws JsonProcessingException {
        // Arrange
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        String cacheKey = "order:cache:" + orderId.value();
        Order orderMock = mock(Order.class);

        when(orderMock.getId()).thenReturn(orderId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(cacheKey)).thenReturn(Mono.just("corrupt-json"));
        when(objectMapper.readValue(anyString(), eq(OrderEntity.class)))
                .thenThrow(new RuntimeException("Deserialization failed"));

        when(repository.findById(orderId)).thenReturn(Mono.just(orderMock));
        when(mapper.toEntity(any(), anyList())).thenReturn(new OrderEntity());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

        // Act
        Mono<Order> result = orderUseCase.getById(orderId);

        // Assert
        StepVerifier.create(result)
                .expectNext(orderMock)
                .verifyComplete();

        verify(repository).findById(orderId);
    }
}