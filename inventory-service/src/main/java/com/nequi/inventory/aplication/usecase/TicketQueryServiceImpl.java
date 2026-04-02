package com.nequi.inventory.aplication.usecase;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.in.TicketQueryService;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TicketQueryServiceImpl implements TicketQueryService {

    private final TicketRepository ticketRepository;

    @Override
    public Mono<Ticket> getTicket(String eventId, String ticketId) {
        return ticketRepository.findById(EventId.of(eventId), ticketId)
                .switchIfEmpty(Mono.error(new BusinessException(
                        "TICKET_NOT_FOUND",
                        "Ticket not found"
                )));
    }

    @Override
    public Mono<String> getTicketStatus(String eventId, String ticketId) {
        return ticketRepository.findById(EventId.of(eventId), ticketId)
                .map(ticket -> ticket.getStatus().name())
                .switchIfEmpty(Mono.error(new BusinessException(
                        "TICKET_NOT_FOUND",
                        "Ticket not found"
                )));
    }
}