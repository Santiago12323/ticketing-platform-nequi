package com.nequi.inventory.domain.port.out;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface TicketRepository {

    Mono<Ticket> findByIdAnEventId(EventId eventId, String ticketId);

    Mono<Ticket> findById(TicketId ticketId);

    Mono<Ticket> save(Ticket ticket);

    Mono<InventoryResponse> reserveAll(EventId eventId, Set<String> ticketIds, OrderId orderId);

    Mono<InventoryResponse> confirmAll(EventId eventId, Set<String> ticketIds, OrderId orderId);

    Mono<Boolean> isAvailable(EventId eventId, String ticketId);

    Flux<Ticket> findAllByEventId(EventId eventId);

    Flux<Ticket> findAvailableByEventId(EventId eventId);
}
