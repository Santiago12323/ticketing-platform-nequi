package com.nequi.ticketing_service.infrastructure.messaging.redis.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisPaymentIngestor {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Void> ingest(UserId userId, EventId eventId, List<String> seatIds, OrderId orderId) {
        return Mono.fromCallable(() -> {
                    ObjectNode node = objectMapper.createObjectNode();

                    node.put("orderId", orderId.value());
                    node.put("userId", userId.value());
                    node.put("eventId", eventId.value());
                    node.put("paymentId", "PAY-" + UUID.randomUUID().toString().substring(0, 8));

                    ArrayNode seatsNode = node.putArray("seatIds");
                    seatIds.forEach(seatsNode::add);

                    node.put("event", OrderEvent.CONFIRM_PAYMENT.name());

                    return node.toString();
                })
                .flatMap(json -> {
                    ObjectRecord<String, String> record = ObjectRecord.create(CacheConstants.PAYMENTS_STREAM, json);
                    return redisTemplate.opsForStream().add(record);
                })
                .then();
    }
}