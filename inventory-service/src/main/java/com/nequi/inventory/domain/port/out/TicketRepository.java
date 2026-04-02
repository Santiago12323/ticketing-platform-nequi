package com.nequi.inventory.domain.port.out;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface TicketRepository {

    Mono<Ticket> findById(EventId eventId, String ticketId);

    Mono<Ticket> save(Ticket ticket);

    Mono<InventoryResponse> reserveAll(EventId eventId, Set<String> ticketIds, String orderId);

    Mono<Void> confirmAll(EventId eventId, Set<String> ticketIds);

    Mono<Boolean> isAvailable(EventId eventId, String ticketId);
}
