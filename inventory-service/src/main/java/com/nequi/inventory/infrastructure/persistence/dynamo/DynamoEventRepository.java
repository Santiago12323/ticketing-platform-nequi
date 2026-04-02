package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import com.nequi.inventory.infrastructure.persistence.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
@RequiredArgsConstructor
public class DynamoEventRepository implements EventRepository {

    private final DynamoDBMapper mapper;
    private final EventMapper entityMapper;


    @Override
    public Mono<Event> findById(EventId id) {
        return Mono.fromCallable(() -> mapper.load(EventEntity.class, id.value()))
                .map(entityMapper::toDomain);
    }

    @Override
    public Mono<Event> save(Event event) {
        return Mono.fromCallable(() -> {
            EventEntity entity = entityMapper.toEntity(event);
            mapper.save(entity);
            return event;
        });
    }

    @Override
    public Mono<Boolean> existsById(String eventId) {
        return Mono.fromCallable(() ->
                mapper.load(EventEntity.class, eventId) != null
        );
    }

    @Override
    public Flux<Event> findAll() {
        return Flux.fromIterable(
                mapper.scan(EventEntity.class, new DynamoDBScanExpression())
        ).map(entityMapper::toDomain);
    }

}
