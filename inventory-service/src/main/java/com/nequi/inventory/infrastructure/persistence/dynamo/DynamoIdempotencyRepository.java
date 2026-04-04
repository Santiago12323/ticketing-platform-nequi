package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.port.out.IdempotencyRepository;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.IdempotencyEntity;
import com.nequi.inventory.infrastructure.persistence.dynamo.utils.DynamoConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class DynamoIdempotencyRepository implements IdempotencyRepository {

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoConstants dynamoConstants;

    private DynamoDbAsyncTable<IdempotencyEntity> table() {
        return enhancedClient.table(
                dynamoConstants.getIdempotencyTable(),
                TableSchema.fromBean(IdempotencyEntity.class)
        );
    }

    @Override
    public Mono<Boolean> saveIfNotExists(OrderId orderId) {
        long expiresAt = Instant.now()
                .plusSeconds(dynamoConstants.getIdempotencyTtlSeconds())
                .getEpochSecond();

        IdempotencyEntity entity = IdempotencyEntity.builder()
                .orderId(orderId.value())
                .expiresAt(expiresAt)
                .build();

        PutItemEnhancedRequest<IdempotencyEntity> request = PutItemEnhancedRequest
                .<IdempotencyEntity>builder(IdempotencyEntity.class)
                .item(entity)
                .conditionExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("attribute_not_exists(orderId)")
                        .build())
                .build();

        return Mono.fromFuture(() -> table().putItem(request))
                .thenReturn(true)
                .onErrorResume(ConditionalCheckFailedException.class, e -> Mono.just(false))
                .onErrorResume(TransactionCanceledException.class, e -> Mono.just(false));
    }

    @Override
    public Mono<Boolean> exists(OrderId orderId) {
        Key key = Key.builder().partitionValue(orderId.value()).build();
        return Mono.fromFuture(() -> table().getItem(key))
                .map(entity -> entity != null)
                .defaultIfEmpty(false);
    }
}