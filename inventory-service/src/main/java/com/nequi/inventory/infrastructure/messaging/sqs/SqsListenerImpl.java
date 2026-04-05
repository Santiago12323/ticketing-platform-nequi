package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.in.IdempotencyService;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.valueobject.*;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.request.InventoryCheckRequest;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.request.InventoryCommand;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsListenerImpl {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final SqsAsyncClient sqsAsyncClient;

    @Value("${statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Value("${spring.cloud.aws.sqs.inventory-request-dlq}")
    private String inventoryRequestDlqUrl;

    @SqsListener("${spring.cloud.aws.sqs.inventory-request-queue}")
    public CompletableFuture<Void> onMessage(String message) {
        return Mono.defer(() -> process(message))
                .doOnError(e -> log.error("[SQS ERROR] Failed to process inventory request: {}", e.getMessage()))
                .onErrorResume(e -> sendToDlq(message).then(Mono.empty()))
                .toFuture();
    }

    private Mono<Void> process(String message) {
        return Mono.fromCallable(() -> objectMapper.readValue(message, InventoryCheckRequest.class))
                .flatMap(request -> switch (request.event()) {
                    case START_PROCESS -> handleReserveFlow(request);
                    case CONFIRM_PAYMENT -> handlePaymentFlow(request);
                    default -> {
                        log.warn("[SQS] Evento no soportado en este listener: {}", request.event());
                        yield Mono.empty();
                    }
                });
    }

    private Mono<Void> handleReserveFlow(InventoryCheckRequest request) {
        return mapToCommand(request)
                .flatMap(cmd -> {
                    return idempotencyService.tryProcess(cmd.orderId())
                            .flatMap(isNew -> Boolean.TRUE.equals(isNew)
                                    ? inventoryService.reserve(cmd.eventId(), cmd.tickets(), cmd.orderId())
                                    : Mono.empty());
                })
                .then();
    }

    private Mono<Void> handlePaymentFlow(InventoryCheckRequest request) {
        return inventoryService.confirm(
                request.eventId(),
                request.requestedTicketIds(),
                request.orderId()
        ).then();
    }


    private Mono<InventoryCommand> mapToCommand(InventoryCheckRequest request) {
        return Mono.fromSupplier(() -> {
            Set<TicketId> tickets = new HashSet<>(request.requestedTicketIds());
            return new InventoryCommand(request.orderId(), request.eventId(), tickets);
        });
    }

    private Mono<Void> sendToDlq(String rawMessage) {
        log.warn("[SQS DLQ] Desviando petición corrupta a: {}", inventoryRequestDlqUrl);

        return Mono.fromCompletionStage(() ->
                        sqsAsyncClient.sendMessage(b -> b
                                .queueUrl(inventoryRequestDlqUrl)
                                .messageBody(rawMessage)
                                .messageGroupId("inventory-request-errors")
                                .messageDeduplicationId("err-" + rawMessage.hashCode() + "-" + System.currentTimeMillis())
                        )
                )
                .doOnSuccess(res -> log.info("[SQS DLQ] Petición enviada a DLQ correctamente"))
                .doOnError(err -> log.error("[SQS DLQ FATAL] Error enviando a DLQ: {}", err.getMessage()))
                .then();
    }

    private void logAudit(String type, String orderId, String eventId) {
        if (auditEnabled) {
            log.info("[SQS AUDIT] Action={}, OrderId={}, EventId={}", type, orderId, eventId);
        }
    }
}