package com.nequi.ticketing_service.infrastructure.messaging.redis.dto;

import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.TicketId;
import com.nequi.ticketing_service.domain.valueobject.UserId;

import java.util.List;

public record OrderData(
        OrderId orderId,
        UserId userId,
        EventId eventId,
        double total,
        String currency,
        List<TicketId> ticketIds
) {}