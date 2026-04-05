package com.nequi.inventory.domain.port.in;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.RequestId;
import com.nequi.inventory.domain.valueobject.TicketId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.List;
import java.util.Set;

public interface InventoryService {

    Mono<Void> reserve(EventId eventId, Set<TicketId> tickets, OrderId orderId);

    Mono<Void> confirm(EventId eventId, List<TicketId> tickets, OrderId orderId);

    Flux<Ticket> getTicketsByEvent(EventId eventId);

    Flux<Ticket> getAvailableTicketsByEvent(EventId eventId);

    Mono<Void> releaseReservedStock(OrderId orderId, Set<TicketId> ticketIds);
}
