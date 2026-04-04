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
                .flatMap(this::mapToCommand)
                .doOnNext(cmd -> {
                    if (auditEnabled) {
                        log.info("[SQS AUDIT] Processing Inventory Command: OrderId={}, EventId={}, TicketsCount={}",
                                cmd.orderId(), cmd.eventId(), cmd.tickets().size());
                    }
                })
                .flatMap(this::handle)
                .then();
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

    private Mono<Void> handle(InventoryCommand cmd) {
        return idempotencyService.tryProcess(cmd.orderId())
                .flatMap(isNew -> {
                    if (Boolean.TRUE.equals(isNew)) {
                        return inventoryService.reserve(cmd.eventId(), cmd.tickets(), cmd.orderId());
                    } else {
                        if (auditEnabled) log.warn("[SQS AUDIT] Skipping duplicate: {}", cmd.orderId());
                        return Mono.empty();
                    }
                }).then();
    }

    private Mono<InventoryCommand> mapToCommand(InventoryCheckRequest request) {
        Set<TicketId> tickets = new HashSet<>(request.requestedTicketIds());
        return Mono.just(new InventoryCommand(request.orderId(), request.eventId(), tickets));
    }
}