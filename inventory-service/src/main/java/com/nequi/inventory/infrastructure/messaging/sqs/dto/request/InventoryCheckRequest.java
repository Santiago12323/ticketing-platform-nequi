package com.nequi.inventory.infrastructure.messaging.sqs.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.enums.OrderEvent;

import java.time.Instant;
import java.util.List;

public record InventoryCheckRequest(
        @JsonProperty("orderId")            OrderId orderId,
        @JsonProperty("eventId")            EventId eventId,
        @JsonProperty("requestedTicketIds") List<TicketId> requestedTicketIds,
        @JsonProperty("event") OrderEvent event,
        @JsonProperty("paymentId")          String paymentId,
        @JsonProperty("createdAt")          Instant createdAt
) {
    public InventoryCheckRequest {
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId is mandatory");
        }
        if (event == null) {
            throw new IllegalArgumentException("OrderEvent is mandatory");
        }

        if (requestedTicketIds == null) requestedTicketIds = List.of();
        if (createdAt == null) createdAt = Instant.now();
    }


    public static InventoryCheckRequest of(OrderId orderId, EventId eventId, List<TicketId> ticketIds) {
        return new InventoryCheckRequest(
                orderId,
                eventId,
                ticketIds,
                OrderEvent.START_PROCESS,
                null,
                Instant.now()
        );
    }

    /**
     * Factory method para el flujo de Pago (CONFIRM_PAYMENT)
     */
    public static InventoryCheckRequest forPayment(OrderId orderId, String paymentId) {
        return new InventoryCheckRequest(
                orderId,
                null,
                List.of(),
                OrderEvent.CONFIRM_PAYMENT,
                paymentId,
                Instant.now()
        );
    }
}