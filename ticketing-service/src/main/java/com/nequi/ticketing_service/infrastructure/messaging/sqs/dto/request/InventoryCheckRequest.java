package com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.request;

import com.nequi.ticketing_service.domain.valueobject.TicketId;

import java.time.Instant;
import java.util.List;

public record InventoryCheckRequest(
        String orderId,
        String eventId,
        List<TicketId> requestedTicketIds,
        Instant createdAt
) {
    public InventoryCheckRequest {
        if (orderId == null || eventId == null || requestedTicketIds == null || requestedTicketIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid InventoryCheckRequest: Missing mandatory fields");
        }
    }

    public static InventoryCheckRequest of(String orderId, String eventId, List<TicketId> seatIds) {
        return new InventoryCheckRequest(
                orderId,
                eventId,
                seatIds,
                Instant.now()
        );
    }
}