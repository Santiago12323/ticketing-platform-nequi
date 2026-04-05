package com.nequi.ticketing_service.domain.model.order;

import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@EqualsAndHashCode(of = "id")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Order {

    private final OrderId id;
    private final UserId userId;
    private final EventId eventId;
    private final Money totalPrice;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<TicketId> ticketIds;

    public static Order create(OrderId orderId,
                               UserId userId,
                               EventId eventId,
                               Money totalPrice,
                               List<TicketId> ticketIds) {
        Instant now = Instant.now();
        return new Order(orderId, userId, eventId, totalPrice, OrderStatus.PENDING_VALIDATION, now, now, ticketIds);
    }


    public static Order reconstruct(OrderId id, UserId userId, EventId eventId, Money totalPrice,
                                    OrderStatus status, Instant createdAt, Instant updatedAt, List<TicketId> ticketIds) {
        return new Order(id, userId, eventId, totalPrice, status, createdAt, updatedAt, ticketIds);
    }

    public void confirmInventory() {
        applyEvent(OrderEvent.VALIDATION_SUCCESS);
    }


    public void confirmPayment() {
        applyEvent(OrderEvent.CONFIRM_PAYMENT);
    }

    public void failPayment() {
        applyEvent(OrderEvent.FAIL_PAYMENT);
    }

    public void failInventory() {
        applyEvent(OrderEvent.VALIDATION_FAILED);
    }

    public void startPayment() {
        applyEvent(OrderEvent.START_PAYMENT);
    }

    public void pay() {
        applyEvent(OrderEvent.CONFIRM_PAYMENT);
    }

    public void cancel() {
        applyEvent(OrderEvent.CANCEL);
    }

    public void expire() {
        applyEvent(OrderEvent.EXPIRE);
    }

    private void applyEvent(OrderEvent event) {
        this.status = this.status.transition(event);
        this.updatedAt = Instant.now();
    }

    public boolean isFinal() {
        return status.isFinalState();
    }
}