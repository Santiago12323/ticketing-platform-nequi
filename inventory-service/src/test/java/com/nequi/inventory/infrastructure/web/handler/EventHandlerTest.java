package com.nequi.inventory.infrastructure.web;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.in.EventService;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.port.in.TicketQueryService;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import com.nequi.inventory.infrastructure.web.dto.Request.CreateEventRequest;
import com.nequi.inventory.infrastructure.web.dto.Response.EventResponse;
import com.nequi.inventory.infrastructure.web.dto.Response.TicketResponse;
import com.nequi.inventory.infrastructure.web.handler.EventHandler;
import com.nequi.inventory.infrastructure.web.mapper.EventResponseMapper;
import com.nequi.inventory.infrastructure.web.mapper.TicketResponseMapper;
import com.nequi.inventory.infrastructure.web.route.EventRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventHandlerTest {

    private WebTestClient webTestClient;

    @Mock
    private EventService eventService;

    @Mock
    private EventResponseMapper eventResponseMapper;

    @Mock
    private TicketQueryService ticketQueryService;

    @Mock
    private TicketResponseMapper ticketResponseMapper;

    @Mock
    private InventoryService inventoryService;

    private final String VALID_UUID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private EventId eventId;

    @BeforeEach
    void setUp() {
        eventId = new EventId(VALID_UUID);

        EventHandler handler = new EventHandler(
                eventService,
                eventResponseMapper,
                ticketQueryService,
                ticketResponseMapper,
                inventoryService
        );

        EventRouter router = new EventRouter();
        this.webTestClient = WebTestClient
                .bindToRouterFunction(router.eventRoutes(handler))
                .build();
    }

    @Test
    @DisplayName("GET /events/{id} - Success")
    void getEventSuccess() {
        Event event = new Event(eventId, "Ev1", "Loc1", 10, EventStatus.ACTIVE);
        EventResponse response = createMockResponse();

        when(eventService.getEvent(any(EventId.class))).thenReturn(Mono.just(event));
        when(eventResponseMapper.toResponse(any(Event.class))).thenReturn(response);

        webTestClient.get()
                .uri("/events/{id}", VALID_UUID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(EventResponse.class);
    }

    @Test
    @DisplayName("GET /events - Success (Stream)")
    void getAllEventsSuccess() {
        Event event = new Event(eventId, "Ev1", "Loc1", 10, EventStatus.ACTIVE);
        EventResponse response = createMockResponse();

        when(eventService.getAllEvents()).thenReturn(Flux.just(event));
        when(eventResponseMapper.toResponse(any(Event.class))).thenReturn(response);

        webTestClient.get()
                .uri("/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    @DisplayName("POST /events - Success")
    void createEventSuccess() {
        CreateEventRequest request = new CreateEventRequest(
                VALID_UUID,
                "Estadio Atanasio",
                "Concierto Rock",
                50000
        );

        when(eventService.createEvent(any(EventId.class), anyInt(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("DELETE /events/{id} - Success")
    void deleteEventSuccess() {
        when(eventService.deleteEvent(any(EventId.class))).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/events/{id}", VALID_UUID)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("GET /events/{eventId}/tickets/{ticketId}/status - Success")
    void getTicketStatusSuccess() {
        String ticketIdStr = "ticket-123";
        String status = "AVAILABLE";

        when(ticketQueryService.getTicketStatus(VALID_UUID, ticketIdStr))
                .thenReturn(Mono.just(status));

        webTestClient.get()
                .uri("/events/{eventId}/tickets/{ticketId}/status", VALID_UUID, ticketIdStr)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(status);
    }

    @Test
    @DisplayName("GET /tickets/stream/{eventId} - Success")
    void getTicketsByEventSuccess() {
        Ticket ticket = Ticket.create(new TicketId("T1"), eventId);
        TicketResponse response = createMockTicketResponse();

        when(inventoryService.getTicketsByEvent(any(EventId.class))).thenReturn(Flux.just(ticket));
        when(ticketResponseMapper.toResponse(any(Ticket.class))).thenReturn(response);

        webTestClient.get()
                .uri("/tickets/stream/{eventId}", VALID_UUID)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    @DisplayName("GET /tickets/stream/available/{eventId} - Success")
    void getAvailableTicketsByEventSuccess() {
        Ticket ticket = Ticket.create(new TicketId("T1"), eventId);
        TicketResponse response = createMockTicketResponse();

        when(inventoryService.getAvailableTicketsByEvent(any(EventId.class))).thenReturn(Flux.just(ticket));
        when(ticketResponseMapper.toResponse(any(Ticket.class))).thenReturn(response);

        webTestClient.get()
                .uri("/tickets/stream/available/{eventId}", VALID_UUID)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    private EventResponse createMockResponse() {
        return new EventResponse(
                eventId,
                "Evento Test",
                "Lugar Test",
                100,
                EventStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );
    }

    private TicketResponse createMockTicketResponse() {
        return new TicketResponse(
                new TicketId("T1"),
                eventId,
                TicketStatus.AVAILABLE,
                Instant.now(),
                Instant.now(),
                false
        );
    }
}