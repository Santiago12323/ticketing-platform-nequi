package com.nequi.inventory.infrastructure.web.handler;

import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.port.in.TicketQueryService;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.web.dto.Response.TicketResponse;
import com.nequi.inventory.infrastructure.web.mapper.TicketResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TicketHandler {

    private final TicketQueryService ticketQueryService;
    private final TicketResponseMapper ticketResponseMapper;
    private final InventoryService inventoryService;

    public Mono<ServerResponse> getTicket(ServerRequest request) {
        return ticketQueryService.getTicket(
                        request.pathVariable("eventId"),
                        request.pathVariable("ticketId")
                )
                .map(ticketResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getTicketStatus(ServerRequest request) {
        return ticketQueryService.getTicketStatus(
                        request.pathVariable("eventId"),
                        request.pathVariable("ticketId")
                )
                .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    public Mono<ServerResponse> getTicketsByEvent(ServerRequest request) {
        EventId eventId = new EventId(request.pathVariable("eventId"));
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(
                        inventoryService.getTicketsByEvent(eventId)
                                .map(ticketResponseMapper::toResponse),
                        TicketResponse.class
                );
    }

    public Mono<ServerResponse> getAvailableTicketsByEvent(ServerRequest request) {
        return ServerResponse.ok()
                .body(
                        Mono.just("trueps"),
                        String.class
                );
    }
}