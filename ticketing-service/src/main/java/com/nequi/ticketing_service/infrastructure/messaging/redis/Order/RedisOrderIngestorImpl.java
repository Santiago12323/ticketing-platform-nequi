package com.nequi.ticketing_service.infrastructure.messaging.redis.Order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.RedisOrderIngestor;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.Money;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.UserId;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisOrderIngestorImpl implements RedisOrderIngestor {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final CacheKeyGenerator keyGenerator;
    @Value("${spring.cloud.aws.redis.orders-stream}")
    private String streamKey;

    @Override
    public Mono<OrderId> ingest(UserId userId, EventId eventId, Money money, List<String> seats, OrderId orderId) {
        return createJsonPayload(userId, eventId, money, seats, orderId)
                .flatMap(json -> {
                    ObjectRecord<String, String> record = StreamRecords.newRecord()
                            .in(streamKey)
                            .ofObject(json);

                    return Mono.zip(
                            redisTemplate.opsForStream().add(record),
                            redisTemplate.opsForValue().set(keyGenerator.generateOrderKey(orderId.value()), json, Duration.ofMinutes(5))
                    ).thenReturn(orderId);
                });
    }

    private Mono<String> createJsonPayload(UserId userId, EventId eventId, Money money, List<String> seats, OrderId orderId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> map = Map.of(
                    "orderId", orderId.value(),
                    "userId", userId.value(),
                    "eventId", eventId.value(),
                    "totalPrice", money.amount(),
                    "currency", money.currency(),
                    "seatIds", seats,
                    "status", "PENDING_VALIDATION",
                    "createdAt", java.time.Instant.now().toString()
            );
            return objectMapper.writeValueAsString(map);
        });
    }
}