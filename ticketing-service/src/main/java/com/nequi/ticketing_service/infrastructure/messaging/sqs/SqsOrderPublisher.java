package com.nequi.ticketing_service.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.request.InventoryCheckRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsOrderPublisher implements OrderPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.inventory-request-queue}")
    private String inventoryQueueUrl;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Override
    public Mono<Void> publishInventoryCheck(OrderId orderId, EventId eventId, List<String> seatIds) {
        return Mono.fromCallable(() -> {
                    InventoryCheckRequest request = InventoryCheckRequest.of(
                            orderId.value(),
                            eventId.value(),
                            seatIds
                    );
                    return objectMapper.writeValueAsString(request);
                })
                .flatMap(json -> {
                    SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                            .queueUrl(inventoryQueueUrl)
                            .messageBody(json)
                            .messageDeduplicationId(orderId.value())
                            .messageGroupId("InventoryGroup")
                            .build();

                    return Mono.fromFuture(() -> sqsClient.sendMessage(sendMsgRequest));
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .jitter(0.75)
                        .doBeforeRetry(retrySignal -> {
                            if (auditEnabled) {
                                log.warn("Retrying SQS publish for order {}. Attempt: {}/3",
                                        orderId.value(), retrySignal.totalRetries() + 1);
                            }
                        })
                )
                .doOnSuccess(response -> {
                    if (auditEnabled) {
                        log.info("Successfully published to SQS. Order: {}, MessageId: {}",
                                orderId.value(), response.messageId());
                    }
                })
                .doOnError(e -> {
                    if (auditEnabled) {
                        log.error("Permanent failure publishing order {} after retries: {}",
                                orderId.value(), e.getMessage());
                    }
                })
                .then()
                .onErrorMap(e -> new RuntimeException("Failed to publish inventory check for order " + orderId.value(), e));
    }
}