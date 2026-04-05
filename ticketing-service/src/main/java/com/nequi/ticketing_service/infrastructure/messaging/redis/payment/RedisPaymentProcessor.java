package com.nequi.ticketing_service.infrastructure.messaging.redis.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.TicketId;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheConstants;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPaymentProcessor {

    private final StreamReceiver<String, ObjectRecord<String, String>> streamReceiver;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final OrderPublisher publisher;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        consumePayments().subscribe();
    }

    private Mono<Void> consumePayments() {
        return streamReceiver.receive(
                        Consumer.from(
                                CacheConstants.ORDER_CONSUMER_GROUP,
                                CacheConstants.ORDER_CONSUMER_NAME
                        ),
                        StreamOffset.create(CacheConstants.PAYMENTS_STREAM, ReadOffset.lastConsumed())
                )
                .flatMap(this::processPaymentRecord)
                .doOnError(e -> log.error("Error fatal en stream de pagos: {}", e.getMessage()))
                .then();
    }

    private Mono<Void> processPaymentRecord(ObjectRecord<String, String> record) {
        return Mono.fromCallable(() -> objectMapper.readTree(record.getValue()))
                .flatMap(node -> {
                    String orderIdStr = node.get("orderId").asText();
                    String paymentId = node.get("paymentId").asText();
                    String eventId = node.get("eventId").asText();
                    List<TicketId> seatIds = new ArrayList<>();
                    if (node.has("seatIds") && node.get("seatIds").isArray()) {
                        node.get("seatIds").forEach(seat -> seatIds.add(TicketId.of(seat.asText())));
                    }

                    log.info("[REDIS_PROCESS] Procesando Order: {} con Payment: {}", orderIdStr, paymentId);

                    return publisher.publishPaymentConfirmed(
                                    OrderId.of(orderIdStr),
                                    paymentId,
                                    EventId.of(eventId),
                                    seatIds
                            )
                            .then(acknowledge(record));
                })
                .doOnSuccess(v -> log.info("[PAYMENT] Registro procesado y confirmado con éxito: {}", record.getId()))
                .onErrorResume(e -> {
                    log.error("Error procesando registro {}: {}", record.getId(), e.getMessage());
                    return Mono.empty();
                });
    }
    private Mono<Void> acknowledge(ObjectRecord<String, String> record) {
        return redisTemplate.opsForStream()
                .acknowledge(
                        CacheConstants.PAYMENTS_STREAM,
                        CacheConstants.ORDER_CONSUMER_GROUP,
                        record.getId()
                )
                .then();
    }
}