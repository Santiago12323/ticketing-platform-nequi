package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.nequi.inventory.domain.model.event.Event;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import com.nequi.inventory.infrastructure.persistence.mapper.EventEntityMapper;
import org.springframework.data.redis.core.RedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class DynamoEventRepository implements EventRepository {

    private final DynamoDBMapper mapper;
    private final EventEntityMapper entityMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public DynamoEventRepository(DynamoDBMapper mapper,
                                 EventEntityMapper entityMapper,
                                 RedisTemplate<String, String> redisTemplate) {
        this.mapper = mapper;
        this.entityMapper = entityMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Event> findById(EventId id) {
        return Mono.fromCallable(() -> mapper.load(EventEntity.class, id.value()))
                .map(entityMapper::toDomain);
    }

    @Override
    public Mono<Event> save(Event event) {
        return Mono.fromCallable(() -> {
            EventEntity entity = entityMapper.toEntity(event);
            DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression()
                    .withExpectedEntry("version",
                            new ExpectedAttributeValue(new AttributeValue().withN(event.getVersion().toString())));
            mapper.save(entity, saveExpression);
            return event;
        });
    }

    @Override
    public Mono<Boolean> isDuplicateRequest(String requestId) {
        return Mono.fromCallable(() -> {
            Boolean exists = redisTemplate.hasKey("req:" + requestId);
            return exists != null && exists;
        });
    }

    @Override
    public Mono<Void> markRequestProcessed(String requestId) {
        return Mono.fromRunnable(() -> {
            redisTemplate.opsForValue().set("req:" + requestId, "processed", Duration.ofHours(24));
        });
    }
}
