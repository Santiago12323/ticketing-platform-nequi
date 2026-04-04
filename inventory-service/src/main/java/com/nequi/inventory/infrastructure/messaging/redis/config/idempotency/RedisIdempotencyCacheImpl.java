package com.nequi.inventory.infrastructure.messaging.redis.config.idempotency;

import com.nequi.inventory.domain.port.out.RedisIdempotency;
import com.nequi.inventory.domain.valueobject.OrderId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisIdempotencyCacheImpl implements RedisIdempotency {

    private final ReactiveRedisTemplate<String, String> redis;

    @Override
    public Mono<Boolean> exists(OrderId orderId) {
        return redis.hasKey(orderId.value());
    }

    @Override
    public Mono<Void> save(OrderId orderId) {
        return redis.opsForValue()
                .set(orderId.value(), "1", Duration.ofMinutes(10))
                .then();
    }
}