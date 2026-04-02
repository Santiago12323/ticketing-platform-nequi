package com.nequi.ticketing_service.infrastructure.messaging.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.machine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheConstants;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOrderProcessor {

    private final StreamReceiver<String, ObjectRecord<String, String>> streamReceiver;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final OrderRepository repository;
    private final OrderPublisher publisher;
    private final OrderStateMachineFactory smFactory;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.aws.redis.orders-stream}")
    private String streamKey;


    @Value("${ticketing.statemachine.audit-enabled}")
    private boolean auditEnabled;

    @PostConstruct
    public void init() {
        this.consumeOrders()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<Void> consumeOrders() {
        return streamReceiver.receive(
                        Consumer.from(CacheConstants.ORDER_CONSUMER_GROUP, CacheConstants.ORDER_CONSUMER_NAME),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                )
                .limitRate(CacheConstants.STREAM_LIMIT_RATE) //blackpresure
                .flatMap(record -> processOrderFromStream(record)
                        .flatMap(unused -> acknowledge(record))
                        .onErrorResume(e -> {
                            logError("Critical error creating initial order", e);
                            return Mono.empty();
                        }), CacheConstants.STREAM_PARALLELISM)
                .then();
    }

    private Mono<Void> processOrderFromStream(ObjectRecord<String, String> record) {
        return Mono.fromCallable(() -> objectMapper.readTree(record.getValue()))
                .flatMap(node -> {
                    OrderId orderId = OrderId.of(node.get("orderId").asText());
                    UserId userId = UserId.of(node.get("userId").asText());
                    EventId eventId = EventId.of(node.get("eventId").asText());
                    Money total = Money.of(node.get("totalPrice").asDouble(), node.get("currency").asText());
                    List<String> seats = objectMapper.convertValue(node.get("seatIds"), List.class);

                    logAudit("Processing order creation for user: {} and event: {}", userId.value(), eventId.value());

                    return Order.create(orderId,userId, eventId, total, smFactory)
                            .flatMap(order -> repository.save(order, seats))
                            .flatMap(savedOrder -> {
                                logAudit("Order {} saved. Publishing to SQS for inventory check.", savedOrder.getId().value());
                                return publisher.publishInventoryCheck(savedOrder.getId(), eventId, seats);
                            });
                });
    }

    private Mono<Void> acknowledge(ObjectRecord<String, String> record) {
        return redisTemplate.opsForStream()
                .acknowledge(streamKey, CacheConstants.ORDER_CONSUMER_GROUP, record.getId())
                .doOnSuccess(v -> logAudit("Message {} acknowledged in Redis", record.getId()))
                .then();
    }

    private void logAudit(String format, Object... args) {
        if (auditEnabled) {
            log.info(format, args);
        }
    }

    private void logError(String message, Throwable e) {
        if (auditEnabled) {
            log.error("{}: {}", message, e.getMessage());
        }
    }
}