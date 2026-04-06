package com.nequi.ticketing_service.application.infrastruture.persistence.dynamo;

import com.nequi.ticketing_service.infrastructure.persistence.dynamo.DynamoOrderHistoryRepository;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderHistoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoOrderHistoryRepositoryTest {

    @Mock
    private DynamoDbAsyncTable<OrderHistoryEntity> historyTable;

    @InjectMocks
    private DynamoOrderHistoryRepository repository;

    private String orderId;
    private final String FROM_STATUS = "PENDING_PAYMENT";
    private final String TO_STATUS = "CONFIRMED";
    private final String DETAILS = "Payment processed successfully";

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("AAA - Success: Should save history record in DynamoDB")
    void saveHistory_ShouldComplete_WhenSuccessful() {
        // Arrange
        when(historyTable.putItem(any(OrderHistoryEntity.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<Void> result = repository.saveHistory(orderId, FROM_STATUS, TO_STATUS, DETAILS);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(historyTable).putItem(argThat((OrderHistoryEntity entity) ->
                entity.getOrderId().equals(orderId) &&
                        entity.getFromStatus().equals(FROM_STATUS) &&
                        entity.getToStatus().equals(TO_STATUS) &&
                        entity.getDetails().equals(DETAILS) &&
                        entity.getCreatedAt() != null
        ));
    }

    @Test
    @DisplayName("AAA - Success: Should return flux of history records for an OrderId")
    @SuppressWarnings("unchecked")
    void findByOrderId_ShouldReturnFlux_WhenFound() {
        // Arrange
        OrderHistoryEntity entity = OrderHistoryEntity.builder()
                .orderId(orderId)
                .fromStatus("START")
                .toStatus("PENDING")
                .build();

        software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher<OrderHistoryEntity> pagePublisher =
                mock(software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher.class);

        Page<OrderHistoryEntity> page = Page.create(List.of(entity));

        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<? super Page<OrderHistoryEntity>> subscriber = invocation.getArgument(0);
            Flux.just(page).subscribe(subscriber);
            return null;
        }).when(pagePublisher).subscribe(any(org.reactivestreams.Subscriber.class));

        when(historyTable.query(any(Consumer.class))).thenReturn(pagePublisher);

        // Act
        Flux<OrderHistoryEntity> result = repository.findByOrderId(orderId);
        StepVerifier.create(result)
                .expectNext(entity)
                .verifyComplete();
    }

    @Test
    @DisplayName("AAA - Success: Should ignore error and complete when Dynamo fails")
    void saveHistory_ShouldComplete_WhenDynamoFails() {
        // Arrange
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Dynamo Write Error"));

        when(historyTable.putItem(any(OrderHistoryEntity.class))).thenReturn(future);

        // Act
        Mono<Void> result = repository.saveHistory(orderId, FROM_STATUS, TO_STATUS, DETAILS);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();
    }
}