package com.nequi.inventory.infrastructure.messaging.sqs.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;

import java.time.Instant;
import java.util.List;

public record InventoryCheckRequest(
        @JsonProperty("orderId")           OrderId orderId,
        @JsonProperty("eventId")           EventId eventId,
        @JsonProperty("requestedTicketIds") List<TicketId> requestedTicketIds,
        @JsonProperty("createdAt")         Instant createdAt
) {
    public InventoryCheckRequest {
        if (orderId == null || eventId == null || requestedTicketIds == null || requestedTicketIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid InventoryCheckRequest: Missing mandatory fields");
        }
        if (createdAt == null) createdAt = Instant.now();
    }

    public static InventoryCheckRequest of(OrderId orderId, EventId eventId, List<TicketId> ticketIds) {
        return new InventoryCheckRequest(orderId, eventId, ticketIds, Instant.now());
    }
}