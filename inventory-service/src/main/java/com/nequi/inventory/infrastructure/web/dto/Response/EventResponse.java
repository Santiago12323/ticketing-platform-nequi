package com.nequi.inventory.infrastructure.web.dto.Response;

import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;

import java.time.Instant;

public record EventResponse(
        EventId eventId,
        String name,
        String location,
        int totalCapacity,
        EventStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}