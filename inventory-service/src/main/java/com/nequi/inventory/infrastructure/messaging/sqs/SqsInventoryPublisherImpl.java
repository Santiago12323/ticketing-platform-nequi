package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.out.SqsInventoryPublisher;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import com.nequi.inventory.infrastructure.messaging.sqs.utils.MessagingProperties;
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
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsInventoryPublisherImpl implements SqsInventoryPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;

    @Override
    public Mono<Void> publishInventoryResponse(InventoryResponse response) {
        return Mono.defer(() -> buildMessage(response))
                .flatMap(body -> sendMessage(body, response))
                .doOnSuccess(resp -> log.info("SQS OK orderId={} messageId={}", response.orderId(), resp.messageId()))
                .doOnError(e -> log.error("SQS FAIL orderId={}", response.orderId(), e))
                .retryWhen(retrySpec(response.orderId()))
                .then();
    }

    private Mono<String> buildMessage(InventoryResponse response) {
        return Mono.fromSupplier(() -> {
            try {
                return objectMapper.writeValueAsString(response);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing InventoryResponse for orderId=" + response.orderId(), e);
            }
        });
    }

    private Mono<SendMessageResponse> sendMessage(String body, InventoryResponse response) {
        String orderIdValue = response.orderId();
        String typeValue = response.type().name();
        String deduplicationId = orderIdValue + ":" + typeValue;
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(properties.getSqs().getResponseQueueUrl())
                .messageBody(body)
                .messageDeduplicationId(deduplicationId)
                .messageGroupId(orderIdValue)
                .messageAttributes(Map.of(
                        "eventType", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("InventoryResponseSent")
                                .build(),
                        "orderId", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(orderIdValue)
                                .build()
                ))
                .build();

        return Mono.fromFuture(() -> sqsClient.sendMessage(request))
                .doOnSubscribe(s -> log.info("Enviando InventoryResponse SQS orderId={}", orderIdValue))
                .doOnSuccess(resp -> log.info("SQS enviado orderId={} messageId={}", orderIdValue, resp.messageId()))
                .doOnError(e -> log.error("Error enviando SQS orderId={}", orderIdValue, e));
    }

    private Retry retrySpec(String orderId) {
        return Retry.backoff(
                        properties.getRetry().getMaxAttempts(),
                        Duration.ofMillis(properties.getRetry().getBackoffMillis())
                )
                .doBeforeRetry(r -> logAudit("Retry {} for orderId={}", r.totalRetries() + 1, orderId));
    }

    private void logAudit(String msg, Object... args) {
        if (properties.getAudit().isEnabled()) {
            log.info(msg, args);
        }
    }
}