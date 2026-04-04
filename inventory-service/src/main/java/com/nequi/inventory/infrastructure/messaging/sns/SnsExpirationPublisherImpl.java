package com.nequi.inventory.infrastructure.messaging.sns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.out.ExpirationPublisher;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnsExpirationPublisherImpl implements ExpirationPublisher {

    private final SnsAsyncClient snsAsyncClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.aws.sns.order-expiration-topic}")
    private String topicArn;

    private static final String MESSAGE_TYPE = "ORDER_EXPIRATION";

    @Override
    public Mono<Void> publishExpirationEvent(OrderId orderId, EventId eventId, Set<TicketId> ticketIds) {
        return buildPayload(orderId, eventId, ticketIds)
                .flatMap(this::sendToSns)
                .doOnSuccess(res -> log.info("[SNS SUCCESS] Expiration event sent for Order: {}", orderId.value()))
                .doOnError(e -> log.error("[SNS ERROR] Failed to publish for Order {}: {}", orderId.value(), e.getMessage()))
                .then();
    }

    private Mono<String> buildPayload(OrderId orderId, EventId eventId, Set<TicketId> ticketIds) {
        return Mono.fromCallable(() -> {
            var rawTicketIds = ticketIds.stream().map(TicketId::value).toList();
            var body = Map.of(
                    "orderId", orderId.value(),
                    "eventId", eventId.value(),
                    "ticketIds", rawTicketIds
            );
            return objectMapper.writeValueAsString(body);
        }).onErrorMap(e -> new RuntimeException("Error serializing SNS payload", e));
    }

    private Mono<Void> sendToSns(String payload) {
        return Mono.fromCompletionStage(() ->
                snsAsyncClient.publish(PublishRequest.builder()
                        .topicArn(topicArn)
                        .message(payload)
                        .messageAttributes(Map.of(
                                "type", MessageAttributeValue.builder()
                                        .dataType("String")
                                        .stringValue(MESSAGE_TYPE)
                                        .build()
                        ))
                        .build()
                )
        ).then();
    }
}