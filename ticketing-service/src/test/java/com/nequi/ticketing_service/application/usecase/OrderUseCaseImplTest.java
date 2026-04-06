package com.nequi.ticketing_service.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderHistoryService;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.port.out.RedisOrderIngestor;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.messaging.redis.payment.RedisPaymentIngestor;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
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
    @Mock private RedisPaymentIngestor redisPaymentIngestor;
    @Mock private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;
    @Mock private OrderRepository repository;
    @Mock private ObjectMapper objectMapper;
    @Mock private OrderEntityMapper mapper;
    @Mock private OrderHistoryService historyService;

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
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("AAA - Success: Should create order intent")
    void createOrderSuccessfully() {
        // Arrange
        OrderId generatedId = OrderId.of(UUID.randomUUID().toString());

        when(redisOrderIngestor.ingest(eq(userId), eq(eventId), eq(price), eq(seats), any(OrderId.class)))
                .thenReturn(Mono.just(generatedId));

        when(historyService.recordTimestamp(any(), any(), any(), anyString()))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(orderUseCase.create(userId, eventId, price, seats))
                .expectNextMatches(id -> id != null) // Verificamos que emita el ID
                .verifyComplete();
        verify(historyService).recordTimestamp(any(), isNull(), eq(OrderStatus.PENDING_PAYMENT), anyString());
    }

    @Test
    @DisplayName("AAA - Success: Should confirm payment intent")
    void confirmAllSuccessfully() {
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        when(redisPaymentIngestor.ingest(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(orderUseCase.confirmAll(userId, eventId, price, seats, orderId))
                .expectNext(orderId)
                .verifyComplete();
    }

    @Test
    @DisplayName("AAA - Success: Should return order from cache")
    void getByIdFromCacheSuccessfully() throws JsonProcessingException {
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        String cacheKey = "order:cache:" + orderId.value();

        when(valueOperations.get(cacheKey)).thenReturn(Mono.just("{}"));
        when(objectMapper.readValue(anyString(), eq(OrderEntity.class))).thenReturn(new OrderEntity());
        when(mapper.toDomain(any())).thenReturn(mock(Order.class));

        StepVerifier.create(orderUseCase.getById(orderId))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("AAA - Success: Should fetch from DB and save to cache if cache is empty")
    void getByIdFromDbWhenCacheEmpty() throws JsonProcessingException {
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        Order orderMock = mock(Order.class);
        when(orderMock.getId()).thenReturn(orderId);

        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(repository.findById(orderId)).thenReturn(Mono.just(orderMock));
        when(mapper.toEntity(any())).thenReturn(new OrderEntity());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(orderUseCase.getById(orderId))
                .expectNext(orderMock)
                .verifyComplete();
    }

    @Test
    @DisplayName("AAA - Error: Should handle repository error in getById")
    void getById_RepositoryError() {
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(repository.findById(orderId)).thenReturn(Mono.error(new RuntimeException("DB Error")));

        StepVerifier.create(orderUseCase.getById(orderId))
                .expectError()
                .verify();
    }

    @Test
    @DisplayName("AAA - Success: Should expire order when valid")
    void expireOrder_Success() {
        // Arrange
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        Order orderMock = mock(Order.class);

        when(orderMock.isFinal()).thenReturn(false);
        when(orderMock.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
        lenient().when(orderMock.getId()).thenReturn(orderId);

        when(repository.findById(orderId)).thenReturn(Mono.just(orderMock));
        when(repository.save(any(Order.class))).thenReturn(Mono.just(orderMock));

        when(historyService.recordTimestamp(any(), any(), any(), anyString()))
                .thenReturn(Mono.empty());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.delete(anyString())).thenReturn(Mono.just(true));

        // Act
        StepVerifier.create(orderUseCase.expireOrder(orderId))
                .verifyComplete();

        // Assert
        verify(orderMock).expire();
        verify(repository).save(orderMock);
        verify(historyService).recordTimestamp(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.EXPIRED), anyString());
        verify(valueOperations).delete(anyString());
    }

    @Test
    @DisplayName("AAA - Skip: Should skip if order is final")
    void expireOrder_SkipIfFinal() {
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        Order orderMock = mock(Order.class);

        when(orderMock.isFinal()).thenReturn(true);
        when(repository.findById(orderId)).thenReturn(Mono.just(orderMock));

        StepVerifier.create(orderUseCase.expireOrder(orderId))
                .verifyComplete();

        verify(orderMock, never()).expire();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("AAA - Skip: Should skip if order not found")
    void expireOrder_NotFound() {
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        when(repository.findById(orderId)).thenReturn(Mono.empty());

        StepVerifier.create(orderUseCase.expireOrder(orderId))
                .verifyComplete();
    }

    @Test
    @DisplayName("AAA - Error: Should handle save error in expireOrder")
    void expireOrder_SaveError() {
        // Arrange
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        Order orderMock = mock(Order.class);

        lenient().when(orderMock.getId()).thenReturn(orderId);

        when(orderMock.isFinal()).thenReturn(false);
        when(orderMock.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);

        when(repository.findById(orderId)).thenReturn(Mono.just(orderMock));

        when(repository.save(any(Order.class)))
                .thenReturn(Mono.error(new RuntimeException("Database Save Fail")));

        // Act & Assert
        StepVerifier.create(orderUseCase.expireOrder(orderId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Database Save Fail"))
                .verify();

        verify(orderMock).expire();
        verify(repository).save(any(Order.class));
    }

    @Test
    @DisplayName("AAA - Fallback: Should fallback to DB if cache serialization fails during save")
    void saveToCache_SerializationError() throws JsonProcessingException {
        // Arrange
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());
        String cacheKey = "order:cache:" + orderId.value();
        Order orderMock = mock(Order.class);
        OrderEntity entityMock = new OrderEntity();

        when(orderMock.getId()).thenReturn(orderId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(cacheKey)).thenReturn(Mono.empty());

        when(repository.findById(orderId)).thenReturn(Mono.just(orderMock));
        when(mapper.toEntity(orderMock)).thenReturn(entityMock);
        when(objectMapper.writeValueAsString(any(OrderEntity.class)))
                .thenThrow(new RuntimeException("Jackson serialization failed"));

        // Act & Assert
        StepVerifier.create(orderUseCase.getById(orderId))
                .expectNext(orderMock)
                .verifyComplete();
        verify(repository).findById(orderId);
        verify(objectMapper).writeValueAsString(entityMock);
        verify(valueOperations, never()).set(eq(cacheKey), anyString(), any());
    }
}