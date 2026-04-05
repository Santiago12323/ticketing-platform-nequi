package com.nequi.ticketing_service.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.in.OrderUseCase;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationSqsListener {

    private final OrderUseCase orderService;
    private final ObjectMapper objectMapper;
    private final SqsAsyncClient sqsAsyncClient;

    @Value("${spring.cloud.aws.sqs.inventory-response-dlq}")
    private String dlqUrl;

    @Value("${ticketing.statemachine.audit-enabled}")
    private boolean auditEnabled;

    @SqsListener("${spring.cloud.aws.sqs.order-ttl-queue}")
    public void onMessageReceived(String message) {
        System.out.println("reved out llego" + message);
        processExpiration(message)
                .onErrorResume(e ->
                    sendToDlq(message, e.getMessage())
                )
                .subscribe();
    }

    private Mono<Void> processExpiration(String message) {
        return Mono.fromCallable(() -> objectMapper.readValue(message, Map.class))
                .flatMap(map -> {
                    String rawOrderId = (String) map.get("orderId");

                    if (rawOrderId == null) {
                        return sendToDlq(message, "Mensaje malformado, falta orderId");
                    }

                    OrderId orderId = new OrderId(rawOrderId);
                    logAudit("[ORDER_TTL] Procesando expiración para orden: {}", orderId.value());

                    return orderService.expireOrder(orderId);
                })
                .doOnSuccess(v -> logAudit("[ORDER_TTL SUCCESS] Expiración completada"));
    }

    private Mono<Void> sendToDlq(String message, String reason) {
        return Mono.fromCompletionStage(() ->
                        sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                                .queueUrl(dlqUrl)
                                .messageBody(message)
                                .messageDeduplicationId(UUID.randomUUID().toString())
                                .messageAttributes(Map.of(
                                        "failureReason", MessageAttributeValue.builder()
                                                .dataType("String")
                                                .stringValue(reason)
                                                .build()
                                ))
                                .build())
                )
                .doOnSuccess(r -> log.warn("[DLQ] Mensaje enviado. Razón: {}", reason))
                .doOnError(e -> log.error("[DLQ ERROR] No se pudo enviar a DLQ: {}", e.getMessage()))
                .then();
    }

    private void logAudit(String message, Object... args) {
        if (auditEnabled) log.info(message, args);
    }
}