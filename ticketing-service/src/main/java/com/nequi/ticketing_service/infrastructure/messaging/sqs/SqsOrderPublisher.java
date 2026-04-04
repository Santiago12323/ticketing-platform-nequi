package com.nequi.ticketing_service.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
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
        return Mono.defer(() -> buildMessage(orderId, eventId, seatIds))
                .flatMap(body -> sendMessage(body, orderId))
                .doOnSuccess(resp -> logAudit("SQS OK order={} messageId={}", orderId.value(), resp.messageId()))
                .doOnError(e -> log.error("SQS FAIL order={}", orderId.value(), e.getMessage()))
                .retryWhen(retrySpec(orderId))
                .then();
    }

    private Mono<String> buildMessage(OrderId orderId, EventId eventId, List<TicketId> seatIds) {
        return Mono.fromSupplier(() -> {
            try {
                return objectMapper.writeValueAsString(
                        InventoryCheckRequest.of(orderId.value(), eventId.value(), seatIds)
                );
            } catch (Exception e) {
                throw new RuntimeException("Error serializing InventoryCheckRequest", e);
            }
        });
    }

    private Mono<SendMessageResponse> sendMessage(String body, OrderId orderId) {

        String orderIdValue = orderId.value();

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(properties.getSqs().getInventoryQueueUrl())
                .messageBody(body)

                .messageDeduplicationId(orderIdValue)
                .messageGroupId(orderIdValue)
                .messageAttributes(Map.of(
                        "eventType", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("InventoryCheckRequested")
                                .build(),
                        "orderId", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(orderIdValue)
                                .build()
                ))

                .build();

        return Mono.fromFuture(() -> sqsClient.sendMessage(request))
                .doOnSubscribe(s -> log.info(" Enviando mensaje SQS orderId={}", orderIdValue))
                .doOnSuccess(resp -> log.info(" SQS enviado orderId={} messageId={}", orderIdValue, resp.messageId()))
                .doOnError(e -> log.error(" Error enviando SQS orderId={}", orderIdValue, e));
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