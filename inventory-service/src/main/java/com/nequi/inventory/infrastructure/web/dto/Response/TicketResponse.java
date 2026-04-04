package com.nequi.inventory.infrastructure.web.dto.Response;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.TicketId;

import java.time.Instant;

public record TicketResponse(
        TicketId ticketId,
        EventId eventId,
        TicketStatus status,
        Instant createdAt,
        Instant updatedAt,
        boolean isFinalState
) {}