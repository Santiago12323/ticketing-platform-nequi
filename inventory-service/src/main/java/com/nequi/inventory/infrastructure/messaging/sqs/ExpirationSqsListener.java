package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.awspring.cloud.sqs.annotation.SqsListener;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirationSqsListener {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final SqsAsyncClient sqsAsyncClient;

    @Value("${spring.cloud.aws.sqs.inventory-ttl-dlq}")
    private String dlqUrl;

    @Value("${ticketing.statemachine.audit-enabled}")
    private boolean auditEnabled;

    @SqsListener("${spring.cloud.aws.sqs.inventory-ttl-queue}")
    public void onMessageReceived(String message) {
        processExpiration(message)
                .onErrorResume(e -> {
                    log.error("[TTL ERROR] Enviando a DLQ: {}", e.getMessage());
                    return sendToDlq(message, e.getMessage());
                })
                .block();
    }

    private Mono<Void> processExpiration(String message) {
        return Mono.fromCallable(() -> objectMapper.readValue(message, Map.class))
                .flatMap(map -> {
                    String orderId   = (String) map.get("orderId");
                    var ticketIdsRaw = (List<String>) map.get("ticketIds");

                    if (orderId == null || ticketIdsRaw == null) {
                        return sendToDlq(message, "Mensaje malformado, faltan campos requeridos");
                    }

                    var ticketIds = ticketIdsRaw.stream()
                            .map(TicketId::new)
                            .collect(Collectors.toSet());

                    logAudit("[TTL] Liberando {} tickets para orden: {}", ticketIds.size(), orderId);

                    return inventoryService.releaseReservedStock(new OrderId(orderId), ticketIds);
                })
                .doOnSuccess(v -> logAudit("[TTL SUCCESS] Flujo de liberación completado"));
    }

    private Mono<Void> sendToDlq(String message, String reason) {
        return Mono.fromCompletionStage(() ->
                        sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                                .queueUrl(dlqUrl)
                                .messageBody(message)
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