package com.nequi.ticketing_service.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.dto.request.InventoryCheckRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

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
                .map(json -> SendMessageRequest.builder()
                        .queueUrl(inventoryQueueUrl)
                        .messageBody(json)
                        .build())
                .flatMap(sendMsgRequest -> Mono.fromFuture(() -> sqsClient.sendMessage(sendMsgRequest)))
                .doOnSuccess(response -> {
                    if (auditEnabled) {
                        log.info("Successfully published inventory check for order {} to queue {}",
                                orderId.value(), inventoryQueueUrl);
                    }
                })
                .doOnError(e -> {
                    if (auditEnabled) {
                        log.error("Error publishing inventory check for order {}: {}",
                                orderId.value(), e.getMessage(), e);
                    }
                })
                .then()
                .onErrorMap(e -> new RuntimeException("Failed to publish inventory check", e));
    }
}
