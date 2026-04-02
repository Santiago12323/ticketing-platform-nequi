package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.RequestId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.request.InventoryCheckRequest;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsInventoryCheckListener {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @SqsListener("${spring.cloud.aws.sqs.inventory-request-queue}")
    public void onMessage(String message) {
        Mono.fromCallable(() -> objectMapper.readValue(message, InventoryCheckRequest.class))
                .flatMap(request -> {
                    EventId eventId = new EventId(request.eventId());

                    Set<TicketId> tickets = request.requestedSeatIds().stream()
                            .map(TicketId::of)
                            .collect(Collectors.toSet());

                    RequestId requestId = new RequestId(request.correlationId());

                    return inventoryService.reserve(eventId, tickets, requestId);
                })
                .doOnError(e -> log.error("Error processing SQS: {}", e.getMessage()))
                .subscribe();
    }
}