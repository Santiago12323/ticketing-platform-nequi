package com.nequi.inventory.infrastructure.web.dto.Response;

import com.nequi.inventory.domain.statemachine.TicketStatus;
import java.time.Instant;

public record TicketResponse(
        String ticketId,
        String eventId,
        TicketStatus status,
        Instant createdAt,
        Instant updatedAt,
        boolean isFinalState
) {}