package com.nequi.inventory.infrastructure.web;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.in.TicketQueryService;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.infrastructure.web.dto.Response.TicketResponse;
import com.nequi.inventory.infrastructure.web.handler.TicketHandler;
import com.nequi.inventory.infrastructure.web.mapper.TicketResponseMapper;
import com.nequi.inventory.infrastructure.web.route.TicketRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketWebHandlerTest {

    private WebTestClient webTestClient;

    @Mock
    private TicketQueryService ticketQueryService;

    @Mock
    private TicketResponseMapper ticketResponseMapper;

    private final String EVENT_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private final String TICKET_ID = "a1b2c3d4-e5f6-4a5b-b6c7-d8e9f0a1b2c3";

    @BeforeEach
    void setUp() {
        TicketHandler handler = new TicketHandler(ticketQueryService, ticketResponseMapper);
        TicketRouter router = new TicketRouter();

        this.webTestClient = WebTestClient
                .bindToRouterFunction(router.ticketRoutes(handler))
                .build();
    }

    @Test
    @DisplayName("GET /events/{eventId}/tickets/{ticketId} - Success")
    void getTicketSuccess() {
        // Given
        Ticket mockTicket = mock(Ticket.class);
        Instant now = Instant.now();

        TicketResponse mockResponse = new TicketResponse(
                TICKET_ID,
                EVENT_ID,
                TicketStatus.AVAILABLE,
                now,
                now,
                false
        );

        when(ticketQueryService.getTicket(EVENT_ID, TICKET_ID)).thenReturn(Mono.just(mockTicket));
        when(ticketResponseMapper.toResponse(mockTicket)).thenReturn(mockResponse);

        // When & Then
        webTestClient.get()
                .uri("/events/{eventId}/tickets/{ticketId}", EVENT_ID, TICKET_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ticketId").isEqualTo(TICKET_ID)
                .jsonPath("$.status").isEqualTo("AVAILABLE")
                .jsonPath("$.isFinalState").isEqualTo(false);
    }

    @Test
    @DisplayName("GET /events/{eventId}/tickets/{ticketId}/status - Success")
    void getTicketStatusSuccess() {
        // Given
        String statusString = "AVAILABLE";
        when(ticketQueryService.getTicketStatus(EVENT_ID, TICKET_ID)).thenReturn(Mono.just(statusString));

        // When & Then
        webTestClient.get()
                .uri("/events/{eventId}/tickets/{ticketId}/status", EVENT_ID, TICKET_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(statusString);
    }

    @Test
    @DisplayName("GET /events/{eventId}/tickets/{ticketId} - 404 Not Found")
    void getTicketNotFound() {
        when(ticketQueryService.getTicket(anyString(), anyString())).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/events/{eventId}/tickets/{ticketId}", EVENT_ID, TICKET_ID)
                .exchange()
                .expectStatus().isNotFound();
    }
}