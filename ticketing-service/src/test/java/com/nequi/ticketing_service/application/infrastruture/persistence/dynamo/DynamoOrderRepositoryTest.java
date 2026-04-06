package com.nequi.ticketing_service.application.infrastruture.persistence.dynamo;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.DynamoOrderRepository;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import com.nequi.ticketing_service.infrastructure.persistence.mapper.OrderEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoOrderRepositoryTest {

    @Mock
    private DynamoDbAsyncTable<OrderEntity> orderTable;

    @Mock
    private OrderEntityMapper mapper;

    @InjectMocks
    private DynamoOrderRepository repository;

    private Order order;
    private OrderEntity entity;
    private OrderId orderId;

    private final String UUID_ORDER = UUID.randomUUID().toString();
    private final String UUID_USER = UUID.randomUUID().toString();
    private final String UUID_EVENT = UUID.randomUUID().toString();
    private final String UUID_TICKET = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        orderId = new OrderId(UUID_ORDER);
        order = Order.create(
                orderId,
                new UserId(UUID_USER),
                new EventId(UUID_EVENT),
                Money.of(new BigDecimal("100.0"), "COP"),
                Collections.singletonList(new TicketId(UUID_TICKET))
        );

        entity = new OrderEntity();
        entity.setId(UUID_ORDER);

        ReflectionTestUtils.setField(repository, "auditEnabled", true);
    }

    @Test
    void save_ShouldReturnOrder_WhenSuccessful() {
        when(mapper.toEntity(order)).thenReturn(entity);
        when(orderTable.putItem(entity)).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(repository.save(order))
                .expectNext(order)
                .verifyComplete();

        verify(orderTable).putItem(entity);
    }

    @Test
    void updateStatus_ShouldReturnOrder_WhenSuccessful() {
        when(mapper.toEntity(order)).thenReturn(entity);
        when(orderTable.updateItem(entity)).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(repository.updateStatus(order))
                .expectNext(order)
                .verifyComplete();
    }

    @Test
    void updateStatus_ShouldThrowRuntimeException_WhenDynamoFails() {
        when(mapper.toEntity(order)).thenReturn(entity);
        CompletableFuture<OrderEntity> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Dynamo Error"));

        when(orderTable.updateItem(entity)).thenReturn(future);

        StepVerifier.create(repository.updateStatus(order))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Error updating order status: " + UUID_ORDER))
                .verify();
    }

    @Test
    void findById_ShouldReturnOrder_WhenFound() {
        when(orderTable.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(entity));
        when(mapper.toDomain(entity)).thenReturn(order);

        StepVerifier.create(repository.findById(orderId))
                .expectNext(order)
                .verifyComplete();
    }

    @Test
    void findById_ShouldReturnEmpty_WhenNotFound() {
        when(orderTable.getItem(any(GetItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(repository.findById(orderId))
                .verifyComplete();

        verify(mapper, never()).toDomain(any());
    }

    @Test
    void logAudit_ShouldNotLog_WhenAuditDisabled() {
        ReflectionTestUtils.setField(repository, "auditEnabled", false);

        when(mapper.toEntity(order)).thenReturn(entity);
        when(orderTable.putItem(entity)).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(repository.save(order))
                .expectNext(order)
                .verifyComplete();
    }
}