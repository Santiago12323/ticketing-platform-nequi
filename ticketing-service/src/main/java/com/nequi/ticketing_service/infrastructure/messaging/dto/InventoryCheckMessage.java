package com.nequi.ticketing_service.infrastructure.messaging.dto;

import java.util.List;

public record InventoryCheckMessage(
        String orderId,
        String eventId,
        List<String> seatIds
) {}
