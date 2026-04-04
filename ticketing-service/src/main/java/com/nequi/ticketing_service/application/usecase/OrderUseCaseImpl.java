package com.nequi.ticketing_service.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.in.OrderUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.port.out.RedisOrderIngestor;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import com.nequi.ticketing_service.infrastructure.persistence.mapper.OrderEntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderUseCaseImpl implements OrderUseCase {

    private final RedisOrderIngestor redisOrderIngestor;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final OrderRepository repository;
    private final ObjectMapper objectMapper;
    private final OrderEntityMapper mapper;

    @Value("${ticketing.statemachine.audit-enabled}")
    private boolean auditEnabled;

    @Value("${ticketing.cache.ttl-minutes}")
    private long cacheTtl;

    @Override
    public Mono<OrderId> create(UserId userId, EventId eventId, Money totalPrice, List<String> seatIds) {
        OrderId orderId = OrderId.of(UUID.randomUUID().toString());

        return redisOrderIngestor.ingest(userId, eventId, totalPrice, seatIds, orderId)
                .doOnSuccess(id -> logAudit("Order intent {} ingested in buffer for user {}", id, userId.value()))
                .thenReturn(orderId);
    }

    @Override
    public Mono<Order> getById(OrderId id) {
        String cacheKey = "order:cache:" + id.value();

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserialize)
                .switchIfEmpty(Mono.defer(() -> fetchFromDbAndCache(id, cacheKey)))
                .doOnError(e -> logError("Error retrieving order " + id.value(), e));
    }


    private Mono<Order> deserialize(String json) {
        return Mono.fromCallable(() -> {
            OrderEntity entity = objectMapper.readValue(json, OrderEntity.class);
            return mapper.toDomain(entity);
        }).onErrorResume(e -> {
            logAudit("Cache corruption, falling back to DB: {}", e.getMessage());
            return Mono.empty();
        });
    }

    private Mono<Order> fetchFromDbAndCache(OrderId id, String cacheKey) {
        return repository.findById(id)
                .flatMap(order -> saveToCache(cacheKey, order)
                        .thenReturn(order));
    }

    private Mono<Void> saveToCache(String key, Order order) {
        return Mono.fromCallable(() -> {
                    OrderEntity entity = mapper.toEntity(order);
                    return objectMapper.writeValueAsString(entity);
                })
                .flatMap(json -> redisTemplate.opsForValue()
                        .set(key, json, Duration.ofMinutes(cacheTtl)))
                .doOnSuccess(s -> logAudit("Order {} successfully cached", order.getId().value()))
                .doOnError(e -> logError("Failed to cache order " + order.getId().value(), e))
                .then();
    }

    private void logAudit(String message, Object... args) {
        if (auditEnabled) log.info(message, args);
    }

    private void logError(String message, Throwable e) {
        if (auditEnabled) log.error("{}: {}", message, e.getMessage());
    }

    @Override
    public Mono<Void> expireOrder(OrderId orderId) {
        return repository.findById(orderId)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        logAudit("[ORDER_EXPIRE] Orden no encontrada: {}", orderId.value())
                ))
                .flatMap(order -> {
                    if (order.isFinal()) {
                        logAudit("[ORDER_EXPIRE] Orden {} ya en estado final: {}",
                                orderId.value(), order.getStatus());
                        return Mono.empty();
                    }

                    if (!order.getStatus().isExpirable()) {
                        logAudit("[ORDER_EXPIRE] Orden {} en estado no expirable: {}",
                                orderId.value(), order.getStatus());
                        return Mono.empty();
                    }

                    order.expire();

                    return repository.save(order)
                            .then(evictCache(orderId))
                            .doOnSuccess(v -> logAudit("[ORDER_EXPIRE] Orden {} expirada: {}",
                                    orderId.value(), order.getStatus()));
                });
    }

    private Mono<Void> evictCache(OrderId orderId) {
        String cacheKey = "order:cache:" + orderId.value();
        return redisTemplate.delete(cacheKey)
                .doOnSuccess(v -> logAudit("[ORDER_EXPIRE] Cache evicted para orden {}", orderId.value()))
                .then();
    }
}