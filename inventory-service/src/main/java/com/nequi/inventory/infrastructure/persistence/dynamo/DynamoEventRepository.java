package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import com.nequi.inventory.infrastructure.persistence.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class DynamoEventRepository implements EventRepository {

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final EventMapper entityMapper;

    private DynamoDbAsyncTable<EventEntity> table() {
        return enhancedClient.table("Event", TableSchema.fromBean(EventEntity.class));
    }

    @Override
    public Mono<Event> findById(EventId id) {
        Key key = Key.builder().partitionValue(id.value()).build();
        return Mono.fromFuture(() -> table().getItem(key))
                .map(entityMapper::toDomain);
    }

    @Override
    public Mono<Event> save(Event event) {
        EventEntity entity = entityMapper.toEntity(event);
        return Mono.fromFuture(() -> table().putItem(entity))
                .thenReturn(event);
    }

    @Override
    public Mono<Boolean> existsById(String eventId) {
        Key key = Key.builder().partitionValue(eventId).build();
        return Mono.fromFuture(() -> table().getItem(key))
                .map(entity -> entity != null)
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<Event> findAll() {
        return Flux.from(table().scan(ScanEnhancedRequest.builder().build()).items())
                .map(entityMapper::toDomain);
    }
}