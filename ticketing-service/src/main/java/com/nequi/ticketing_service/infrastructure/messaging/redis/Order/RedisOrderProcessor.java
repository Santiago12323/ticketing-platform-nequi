package com.nequi.ticketing_service.infrastructure.messaging.redis.Order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.messaging.redis.dto.OrderData;
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

import java.util.List;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOrderProcessor {

    private final StreamReceiver<String, ObjectRecord<String, String>> streamReceiver;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final OrderRepository repository;
    private final OrderPublisher publisher;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.aws.redis.orders-stream}")
    private String streamKey;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @PostConstruct
    public void init() {
        consumeOrders()
                .subscribe();
    }

    private Mono<Void> consumeOrders() {
        return streamReceiver.receive(
                        Consumer.from(CacheConstants.ORDER_CONSUMER_GROUP, CacheConstants.ORDER_CONSUMER_NAME),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                )
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Stream fatal error", e))
                .then();
    }

    private Mono<Void> processRecord(ObjectRecord<String, String> record) {
        return parse(record)
                .flatMap(this::createAndPublishOrder)
                .then(acknowledge(record))
                .onErrorResume(e -> {
                    log.error("Error processing record {}", record.getId(), e);
                    return Mono.empty();
                });
    }

    private Mono<OrderData> parse(ObjectRecord<String, String> record) {
        return Mono.fromCallable(() -> {
            JsonNode node = objectMapper.readTree(record.getValue());

            return new OrderData(
                    OrderId.of(node.get("orderId").asText()),
                    UserId.of(node.get("userId").asText()),
                    EventId.of(node.get("eventId").asText()),
                    node.get("totalPrice").asDouble(),
                    node.get("currency").asText(),
                    mapSeats(node.get("seatIds"))
            );
        });
    }

    private List<TicketId> mapSeats(JsonNode seatNodes) {
        if (seatNodes == null || !seatNodes.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(seatNodes.spliterator(), false)
                .map(node -> TicketId.of(node.asText()))
                .toList();
    }

    private Mono<Void> createAndPublishOrder(OrderData data) {
        Order order = Order.create(
                data.orderId(),
                data.userId(),
                data.eventId(),
                Money.of(data.total(), data.currency()),
                data.ticketIds()
        );

        return repository.save(order)
                .flatMap(saved -> publisher.publishInventoryCheck(
                        saved.getId(),
                        saved.getEventId(),
                        saved.getTicketIds()))
                .doOnSuccess(v -> logAudit("Order {} created and published to inventory", order.getId().value()))
                .then();
    }

    private Mono<Void> acknowledge(ObjectRecord<String, String> record) {
        return redisTemplate.opsForStream()
                .acknowledge(streamKey, CacheConstants.ORDER_CONSUMER_GROUP, record.getId())
                .doOnSuccess(v -> logAudit("ACK record {}", record.getId()))
                .then();
    }

    private void logAudit(String msg, Object... args) {
        if (auditEnabled) log.info(msg, args);
    }
}