package com.nequi.inventory.domain.port.in;

import com.nequi.inventory.domain.model.Ticket;
import reactor.core.publisher.Mono;

public interface TicketQueryService {

    Mono<Ticket> getTicket(String eventId, String ticketId);

    Mono<String> getTicketStatus(String eventId, String ticketId);
}