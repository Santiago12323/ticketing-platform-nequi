package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.IdempotencyEntity;
import com.nequi.inventory.infrastructure.persistence.dynamo.utils.DynamoConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class DynamoIdempotencyRepositoryTest {

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @Mock
    private DynamoConstants dynamoConstants;

    @Mock
    private DynamoDbAsyncTable<IdempotencyEntity> table;

    private DynamoIdempotencyRepository repository;

    private OrderId orderId;
    private IdempotencyEntity sampleEntity;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        repository = new DynamoIdempotencyRepository(enhancedClient, dynamoConstants);

        orderId = OrderId.of("11111111-1111-1111-1111-111111111111");

        sampleEntity = IdempotencyEntity.builder()
                .orderId(orderId.value())
                .expiresAt(System.currentTimeMillis())
                .build();

        when(dynamoConstants.getIdempotencyTable()).thenReturn("IdempotencyTable");
        when(dynamoConstants.getIdempotencyTtlSeconds()).thenReturn(3600L);

        when((DynamoDbAsyncTable<IdempotencyEntity>)(DynamoDbAsyncTable<?>) enhancedClient.table(anyString(), any(TableSchema.class)))
                .thenReturn(table);
    }

    @Test
    void testSaveIfNotExists_success() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(sampleEntity));

        StepVerifier.create(repository.saveIfNotExists(orderId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testSaveIfNotExists_conditionalCheckFailed() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        ConditionalCheckFailedException.builder().message("exists").build()
                ));

        StepVerifier.create(repository.saveIfNotExists(orderId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testSaveIfNotExists_transactionCanceled() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        TransactionCanceledException.builder().message("canceled").build()
                ));

        StepVerifier.create(repository.saveIfNotExists(orderId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testExists_true() {
        when(table.getItem(any(Key.class)))
                .thenReturn(CompletableFuture.completedFuture(sampleEntity));

        StepVerifier.create(repository.exists(orderId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testExists_false() {
        when(table.getItem(any(Key.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(repository.exists(orderId))
                .expectNext(false)
                .verifyComplete();
    }
}