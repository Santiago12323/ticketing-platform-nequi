package com.nequi.inventory.domain.model;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.statemachine.TicketEvent;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import lombok.*;


import java.time.Instant;

@Getter
@EqualsAndHashCode(of = "ticketId")
@RequiredArgsConstructor
public class Ticket {

    private final TicketId ticketId;
    private final EventId eventId;

    private TicketStatus status;
    private String userId;
    private OrderId orderId;
    private String expiresAt;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static Ticket create(TicketId ticketId, EventId eventId) {
        Ticket ticket    = new Ticket(ticketId, eventId);
        ticket.status    = TicketStatus.AVAILABLE;
        ticket.createdAt = Instant.now();
        ticket.updatedAt = ticket.createdAt;
        return ticket;
    }

    public boolean canBeReleasedBy(OrderId orderId) {
        return isReserved() && orderId.equals(this.orderId);
    }

    public boolean isReserved() {
        return this.status == TicketStatus.RESERVED;
    }

    public static Ticket reconstitute(
            TicketId ticketId,
            EventId eventId,
            TicketStatus status,
            String userId,
            OrderId orderId,
            String expiresAt,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        Ticket ticket    = new Ticket(ticketId, eventId);
        ticket.status    = status;
        ticket.userId    = userId;
        ticket.orderId   = orderId;
        ticket.expiresAt = expiresAt;
        ticket.version   = version;
        ticket.createdAt = createdAt;
        ticket.updatedAt = updatedAt;
        return ticket;
    }



    public void reserve(Event event) {
        validateEvent(event);
        apply(TicketEvent.RESERVE);
    }

    public void startPayment(Event event) {
        validateEvent(event);
        apply(TicketEvent.START_PAYMENT);
    }

    public void confirmPayment() {
        apply(TicketEvent.CONFIRM_PAYMENT);
    }

    public void cancel() {
        apply(TicketEvent.CANCEL_RESERVATION);
    }

    public void expire() {
        apply(TicketEvent.EXPIRE_RESERVATION);
    }

    public void failPayment() {
        apply(TicketEvent.FAIL_PAYMENT);
    }

    public void assignComplimentary(Event event) {
        validateEvent(event);
        apply(TicketEvent.ASSIGN_COMPLIMENTARY);
    }

    public boolean isFinal() {
        return status.isFinalState();
    }

    private void apply(TicketEvent event) {
        this.status    = this.status.transition(event);
        this.updatedAt = Instant.now();
    }

    private void validateEvent(Event event) {
        if (event == null)
            throw new BusinessException("EVENT_NOT_FOUND", "Event does not exist");
        if (event.getStatus() != EventStatus.ACTIVE)
            throw new BusinessException("EVENT_NOT_ACTIVE", "Event is not available");
    }
}