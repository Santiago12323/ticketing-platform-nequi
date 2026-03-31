package com.nequi.inventory.infrastructure.persistence.redis;

import com.nequi.inventory.domain.model.event.Event;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.valueobject.EventId;

import com.nequi.inventory.infrastructure.persistence.dynamo.DynamoEventRepository;
import org.springframework.data.redis.core.RedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class RedisEventRepository implements EventRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final DynamoEventRepository dynamoRepository;

    public RedisEventRepository(RedisTemplate<String, String> redisTemplate,
                                DynamoEventRepository dynamoRepository) {
        this.redisTemplate = redisTemplate;
        this.dynamoRepository = dynamoRepository;
    }

    @Override
    public Mono<Event> findById(EventId id) {
        return dynamoRepository.findById(id);
    }

    @Override
    public Mono<Event> save(Event event) {
        return dynamoRepository.save(event);
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
            // TTL configurable: por ejemplo 24h
            redisTemplate.opsForValue().set("req:" + requestId, "processed", Duration.ofHours(24));
        });
    }
}
