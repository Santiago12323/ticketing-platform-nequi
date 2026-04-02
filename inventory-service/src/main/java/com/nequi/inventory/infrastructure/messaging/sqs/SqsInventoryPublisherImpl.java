package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.out.SqsInventoryPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;


import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsInventoryPublisherImpl implements SqsInventoryPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.aws.sqs.inventory-request-queue}")
    private String inventoryRequestQueueUrl;

    @Value("${aws.sqs.inventory-response-queue}")
    private String inventoryResponseQueueUrl;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    private static final int MAX_RETRIES = 3;

    @Override
    public Mono<Void> publishInventoryResponse(InventoryResponse response) {

        return sendMessage(response, inventoryResponseQueueUrl, response.orderId(), "OrderGroup")
                .doOnSuccess(msgId ->
                        logAudit("InventoryResponse sent -> orderId: {}, success: {}, failedTickets: {}, messageId: {}",
                                response.orderId(),
                                response.success(),
                                response.failedTicketIds(),
                                msgId)
                )
                .doOnError(e ->
                        logError("Failed to publish InventoryResponse for order " + response.orderId(), e)
                )
                .then();
    }

    private Mono<String> sendMessage(Object payload,
                                     String queueUrl,
                                     String dedupId,
                                     String groupId) {

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))

                .flatMap(json -> {

                    logAudit("Sending SQS message -> queue: {}, dedupId: {}, payload: {}",
                            queueUrl, dedupId, json);

                    SendMessageRequest request = SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(json)
                            .messageDeduplicationId(dedupId)
                            .messageGroupId(groupId)
                            .build();

                    return Mono.fromFuture(() -> sqsClient.sendMessage(request))
                            .map(response -> response.messageId());
                })

                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(100))
                        .jitter(0.75)
                        .doBeforeRetry(signal ->
                                logAudit("Retrying SQS send -> dedupId: {}, attempt: {}/{}",
                                        dedupId,
                                        signal.totalRetries() + 1,
                                        MAX_RETRIES)
                        )
                )

                .doOnError(e ->
                        logError("SQS send FAILED after retries -> dedupId: " + dedupId, e)
                );
    }

    private void logAudit(String format, Object... args) {
        if (auditEnabled) {
            log.info(format, args);
        }
    }

    private void logError(String message, Throwable e) {
        if (auditEnabled) {
            log.error("{}: {}", message, e.getMessage(), e);
        }
    }
}