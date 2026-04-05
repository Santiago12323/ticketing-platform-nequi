package com.nequi.ticketing_service.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.TicketId;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.request.InventoryCheckRequest;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.utils.MessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsOrderPublisher implements OrderPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;


    @Override
    public Mono<Void> publishInventoryCheck(OrderId orderId, EventId eventId, List<TicketId> seatIds) {
        InventoryCheckRequest payload = InventoryCheckRequest.of(
                orderId.value(),
                eventId.value(),
                OrderEvent.START_PROCESS,
                seatIds
        );
        return executePublish(orderId, payload, "InventoryCheckRequested");
    }

    @Override
    public Mono<Void> publishPaymentConfirmed(OrderId orderId, String paymentId, EventId eventId, List<TicketId> seatIds) {
        InventoryCheckRequest payload = InventoryCheckRequest.of(
                orderId.value(),
                eventId.value(),
                OrderEvent.CONFIRM_PAYMENT,
                seatIds
        );
        return executePublish(orderId, payload, "PaymentConfirmed");
    }

    private Mono<Void> executePublish(OrderId orderId, InventoryCheckRequest payload, String eventType) {
        return buildMessage(payload)
                .flatMap(body -> sendMessage(body, orderId, eventType))
                .doOnSuccess(resp -> logAudit("SQS OK order={} event={} messageId={}", orderId.value(), eventType, resp.messageId()))
                .doOnError(e -> log.error("SQS FAIL order={} event={}", orderId.value(), eventType, e.getMessage()))
                .retryWhen(retrySpec(orderId))
                .then();
    }

    private Mono<String> buildMessage(InventoryCheckRequest payload) {
        return Mono.fromSupplier(() -> {
            try {
                return objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing request", e);
            }
        });
    }

    private Mono<SendMessageResponse> sendMessage(String body, OrderId orderId, String eventType) {
        String orderIdValue = orderId.value();

        String deduplicationId = orderIdValue + ":" + eventType;

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(properties.getSqs().getInventoryQueueUrl())
                .messageBody(body)
                .messageDeduplicationId(deduplicationId)
                .messageGroupId(orderIdValue)
                .messageAttributes(Map.of(
                        "eventType", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(eventType)
                                .build(),
                        "orderId", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(orderIdValue)
                                .build()
                ))
                .build();

        return Mono.fromFuture(() -> sqsClient.sendMessage(request))
                .doOnSubscribe(s -> log.info(" Enviando SQS {} orderId={}", eventType, orderIdValue));
    }

    private Retry retrySpec(OrderId orderId) {
        return Retry.backoff(
                        properties.getRetry().getMaxAttempts(),
                        Duration.ofMillis(properties.getRetry().getBackoffMillis())
                )
                .doBeforeRetry(r -> logAudit("Retry {} for order {}", r.totalRetries() + 1, orderId.value()));
    }

    private void logAudit(String msg, Object... args) {
        if (properties.getAudit().isEnabled()) {
            log.info(msg, args);
        }
    }
}