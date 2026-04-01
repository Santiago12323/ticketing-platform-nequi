package com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryCheckRequest(
        String orderId,
        String eventId,
        List<String> requestedSeatIds,
        String correlationId,
        Instant createdAt
) {
    public InventoryCheckRequest {
        if (orderId == null || eventId == null || requestedSeatIds == null || requestedSeatIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid InventoryCheckRequest: Missing mandatory fields");
        }
    }

    public static InventoryCheckRequest of(String orderId, String eventId, List<String> seatIds) {
        return new InventoryCheckRequest(
                orderId,
                eventId,
                seatIds,
                UUID.randomUUID().toString(),
                Instant.now()
        );
    }
}