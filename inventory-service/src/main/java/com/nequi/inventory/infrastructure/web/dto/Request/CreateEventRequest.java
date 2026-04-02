package com.nequi.inventory.infrastructure.web.dto.Request;

public record CreateEventRequest(
        String eventId,
        String name,
        String location,
        int totalCapacity
) {
}