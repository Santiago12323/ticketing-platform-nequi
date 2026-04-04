package com.nequi.inventory.infrastructure.messaging.sqs.dto.request;

import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.RequestId;
import com.nequi.inventory.domain.valueobject.TicketId;

import java.util.Set;

public record InventoryCommand(
        OrderId orderId,
        EventId eventId,
        Set<TicketId> tickets
) {}