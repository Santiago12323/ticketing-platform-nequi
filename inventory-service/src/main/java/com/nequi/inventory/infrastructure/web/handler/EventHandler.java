package com.nequi.inventory.infrastructure.web.handler;

import com.nequi.inventory.domain.port.in.EventService;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.port.in.TicketQueryService;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.web.dto.Request.CreateEventRequest;
import com.nequi.inventory.infrastructure.web.dto.Response.EventResponse;
import com.nequi.inventory.infrastructure.web.dto.Response.TicketResponse;
import com.nequi.inventory.infrastructure.web.mapper.EventResponseMapper;
import com.nequi.inventory.infrastructure.web.mapper.TicketResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class EventHandler {

    private final EventService eventService;
    private final EventResponseMapper eventResponseMapper;
    private final TicketQueryService ticketQueryService;
    private final TicketResponseMapper ticketResponseMapper;
    private final InventoryService inventoryService;

    public Mono<ServerResponse> getEvent(ServerRequest request) {
        EventId eventId = new EventId(request.pathVariable("id"));

        return eventService.getEvent(eventId)
                .map(eventResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> getAllEvents(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(
                        eventService.getAllEvents()
                                .map(eventResponseMapper::toResponse),
                        EventResponse.class
                );
    }


    public Mono<ServerResponse> createEvent(ServerRequest request) {
        return request.bodyToMono(CreateEventRequest.class)
                .flatMap(req -> eventService.createEvent(
                        new EventId(req.eventId()),
                        req.totalCapacity(),
                        req.name(),
                        req.location()
                ))
                .then(ServerResponse.ok().build());
    }


    public Mono<ServerResponse> deleteEvent(ServerRequest request) {
        EventId eventId = new EventId(request.pathVariable("id"));

        return eventService.deleteEvent(eventId)
                .then(ServerResponse.noContent().build());
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
        EventId eventId = new EventId(request.pathVariable("eventId"));

        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(
                        inventoryService.getAvailableTicketsByEvent(eventId)
                                .map(ticketResponseMapper::toResponse),
                        TicketResponse.class
                );
    }
}