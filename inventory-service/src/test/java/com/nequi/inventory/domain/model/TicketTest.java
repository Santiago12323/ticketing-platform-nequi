package com.nequi.inventory.domain.model;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketTest {

    private final TicketId ticketId = TicketId.of("T-123");
    private final EventId eventId = EventId.of(UUID.randomUUID().toString());
    private final OrderId orderId = OrderId.of(UUID.randomUUID().toString());
    private Event activeEvent;

    @BeforeEach
    void setUp() {
        activeEvent = mock(Event.class);
        when(activeEvent.getStatus()).thenReturn(EventStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should create a new ticket with AVAILABLE status")
    void createTicketSuccess() {
        Ticket ticket = Ticket.create(ticketId, eventId);

        assertAll(
                () -> assertEquals(ticketId, ticket.getTicketId()),
                () -> assertEquals(eventId, ticket.getEventId()),
                () -> assertEquals(TicketStatus.AVAILABLE, ticket.getStatus()),
                () -> assertNotNull(ticket.getCreatedAt()),
                () -> assertEquals(ticket.getCreatedAt(), ticket.getUpdatedAt())
        );
    }

    @Test
    @DisplayName("Should reconstitute ticket with all fields")
    void reconstituteTicket() {
        Instant now = Instant.now();
        Ticket ticket = Ticket.reconstitute(
                ticketId, eventId, TicketStatus.RESERVED, "user-1",
                orderId, "2026-12-31", 1L, now, now
        );

        assertAll(
                () -> assertEquals(TicketStatus.RESERVED, ticket.getStatus()),
                () -> assertEquals(orderId, ticket.getOrderId()),
                () -> assertEquals("user-1", ticket.getUserId()),
                () -> assertTrue(ticket.isReserved())
        );
    }

    @Test
    @DisplayName("Should reserve ticket successfully")
    void reserveSuccess() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);
        assertEquals(TicketStatus.RESERVED, ticket.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when event is null (validateEvent)")
    void validateEventNull() {
        Ticket ticket = Ticket.create(ticketId, eventId);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ticket.reserve(null));

        assertEquals("EVENT_NOT_FOUND", exception.getErrorCode());
        assertEquals("Event does not exist", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when event is not ACTIVE (validateEvent)")
    void validateEventNotActive() {
        Ticket ticket = Ticket.create(ticketId, eventId);

        Event inactiveEvent = mock(Event.class);
        when(inactiveEvent.getStatus()).thenReturn(EventStatus.CANCELLED);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ticket.reserve(inactiveEvent));

        assertEquals("EVENT_NOT_ACTIVE", exception.getErrorCode());
        assertEquals("Event is not available", exception.getMessage());
    }

    @Test
    @DisplayName("Should verify canBeReleasedBy logic")
    void testCanBeReleasedBy() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);
        ticket.setOrderId(orderId);

        assertTrue(ticket.canBeReleasedBy(orderId), "Should be released by the same orderId");
        assertFalse(ticket.canBeReleasedBy(OrderId.of(UUID.randomUUID().toString())), "Should NOT be released by different orderId");

        ticket.confirmPayment();
        assertFalse(ticket.canBeReleasedBy(orderId), "Should NOT be released if status is not RESERVED");
    }

    @Test
    @DisplayName("Should throw exception when reserving a non-available ticket")
    void reserveFailAlreadyReserved() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ticket.reserve(activeEvent));

        assertEquals("TICKET_NOT_AVAILABLE", exception.getErrorCode());
    }

    @Test
    @DisplayName("Should transition through full payment flow")
    void paymentFlowSuccess() {
        Ticket ticket = Ticket.create(ticketId, eventId);

        ticket.reserve(activeEvent);
        ticket.startPayment(activeEvent);
        assertEquals(TicketStatus.PENDING_CONFIRMATION, ticket.getStatus());

        ticket.confirmPayment();
        assertEquals(TicketStatus.SOLD, ticket.getStatus());
        assertTrue(ticket.isFinal());
    }

    @Test
    @DisplayName("Should handle cancellation and expiration from RESERVED")
    void cancelAndExpireFromReservedFlow() {
        Ticket ticket = Ticket.create(ticketId, eventId);

        ticket.reserve(activeEvent);
        ticket.cancel();
        assertEquals(TicketStatus.AVAILABLE, ticket.getStatus());

        ticket.reserve(activeEvent);
        ticket.expire();
        assertEquals(TicketStatus.AVAILABLE, ticket.getStatus());
    }

    @Test
    @DisplayName("Should handle expiration from PENDING_CONFIRMATION")
    void expireFromPendingConfirmationFlow() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);
        ticket.startPayment(activeEvent);

        ticket.expire();
        assertEquals(TicketStatus.AVAILABLE, ticket.getStatus());
    }

    @Test
    @DisplayName("Should handle failed payments in PENDING_CONFIRMATION")
    void failPaymentFlow() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);
        ticket.startPayment(activeEvent);

        ticket.failPayment();
        assertEquals(TicketStatus.AVAILABLE, ticket.getStatus());
    }

    @Test
    @DisplayName("Should throw exception on invalid transition (e.g. Cancel from SOLD)")
    void invalidTransitionFlow() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);
        ticket.confirmPayment();

        BusinessException exception = assertThrows(BusinessException.class, ticket::cancel);
        assertEquals("TICKET_EVENT_NOT_ACCEPTED", exception.getErrorCode());
    }

    @Test
    @DisplayName("Should allow release when orderId matches and ticket is RESERVED")
    void canBeReleasedBySuccess() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);
        ticket.setOrderId(orderId);

        assertTrue(ticket.canBeReleasedBy(orderId));
    }

    @Test
    @DisplayName("Should NOT allow release when orderId is different")
    void canBeReleasedByDifferentOrder() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.reserve(activeEvent);
        ticket.setOrderId(orderId);

        OrderId differentOrder = OrderId.of(UUID.randomUUID().toString());

        assertFalse(ticket.canBeReleasedBy(differentOrder));
    }

    @Test
    @DisplayName("Should NOT allow release when status is not RESERVED")
    void canBeReleasedByWrongStatus() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.setOrderId(orderId);

        assertFalse(ticket.canBeReleasedBy(orderId));
    }

    @Test
    @DisplayName("Should assign complimentary ticket")
    void assignComplimentarySuccess() {
        Ticket ticket = Ticket.create(ticketId, eventId);
        ticket.assignComplimentary(activeEvent);
        assertEquals(TicketStatus.COMPLIMENTARY, ticket.getStatus());
        assertTrue(ticket.isFinal());
    }
}