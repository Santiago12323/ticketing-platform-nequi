package com.nequi.ticketing_service.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.in.ProcessInventoryResponseUseCase;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;


@Slf4j
@Component
@RequiredArgsConstructor
public class SqsInventoryResponseListener {

    private final ProcessInventoryResponseUseCase processUseCase;
    private final ObjectMapper objectMapper;
    private final SqsAsyncClient sqsAsyncClient;

    @Value("${ticketing.statemachine.audit-enabled}")
    private boolean auditEnabled;

    @Value("${spring.cloud.aws.sqs.inventory-response-dlq}")
    private String inventoryResponseDlqUrl;

    @SqsListener("${spring.cloud.aws.sqs.inventory-response-queue}")
    public void onMessage(String message) {

        if (auditEnabled) {
            log.info("[SQS AUDIT] Raw message received from inventory-response-queue: {}", message);
        }

        Mono.defer(() -> process(message))
                .doOnError(e -> log.error("[SQS ERROR] Failed to process inventory response: {}", e.getMessage()))
                .onErrorResume(e -> sendToDlq(message).then(Mono.empty()))
                .subscribe();
    }

    private Mono<Void> process(String message) {
        return Mono.fromCallable(() -> objectMapper.readValue(message, InventoryResponse.class))
                .doOnNext(response -> {
                    if (auditEnabled) {
                        log.info("[SQS AUDIT] Processing InventoryResponse: OrderId={}, Success={}, FailedTickets={}",
                                response.orderId(), response.success(), response.failedTicketIds());
                    }
                })
                .flatMap(response -> processUseCase.execute(response.orderId(), response.success()))
                .then();
    }

    private Mono<Void> sendToDlq(String rawMessage) {
        return Mono.fromCompletionStage(() ->
                        sqsAsyncClient.sendMessage(b -> b
                                .queueUrl(inventoryResponseDlqUrl)
                                .messageBody(rawMessage)
                                .messageGroupId("inventory-response-errors")
                                .messageDeduplicationId(String.valueOf(rawMessage.hashCode() + System.currentTimeMillis()))
                        )
                )
                .doOnSuccess(res -> log.info("[SQS] Mensaje movido a DLQ con éxito"))
                .doOnError(err -> log.error("[SQS FATAL] No se pudo mover a DLQ: {}", err.getMessage()))
                .then();
    }
}