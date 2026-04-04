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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsInventoryCheckListener {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;

    @Value("${statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @SqsListener("${spring.cloud.aws.sqs.inventory-request-queue}")
    public void onMessage(String message) {

        if (auditEnabled) {
            log.info("[SQS AUDIT] Raw message received from inventory-request-queue: {}", message);
        }

        Mono.defer(() -> process(message))
                .doOnError(e -> log.error("[SQS ERROR] Failed to process message: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
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

    private Mono<Void> handle(InventoryCommand cmd) {
        return idempotencyService.tryProcess(cmd.orderId())
                .flatMap(isNew -> {
                    if (Boolean.TRUE.equals(isNew)) {
                        return inventoryService.reserve(
                                cmd.eventId(),
                                cmd.tickets(),
                                cmd.orderId()
                        );
                    } else {
                        if (auditEnabled) {
                            log.warn("[SQS AUDIT] Skipping duplicate message for OrderId: {}", cmd.orderId());
                        }
                        return Mono.empty();
                    }
                })
                .then();
    }

    private Mono<InventoryCommand> mapToCommand(InventoryCheckRequest request) {
        Set<TicketId> tickets = new HashSet<>(request.requestedTicketIds());
        return Mono.just(new InventoryCommand(request.orderId(), request.eventId(), tickets));
    }
}