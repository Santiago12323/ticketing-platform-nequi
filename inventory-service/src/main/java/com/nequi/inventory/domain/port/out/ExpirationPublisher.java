package com.nequi.inventory.domain.port.out;

import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface ExpirationPublisher {
    Mono<Void> publishExpirationEvent(OrderId orderId, EventId eventId, Set<TicketId> ticketIds);
}
