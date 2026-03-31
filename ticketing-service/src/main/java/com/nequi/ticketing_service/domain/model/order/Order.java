package com.nequi.ticketing_service.domain.model.order;

import com.nequi.ticketing_service.domain.statemachine.*;
import com.nequi.ticketing_service.domain.valueobject.*;

import org.springframework.statemachine.StateMachine;

import java.time.Instant;
import java.util.Objects;


public class Order {

    private final OrderId id;
    private final UserId userId;
    private final EventId eventId;
    private Money totalPrice;

    private final StateMachine<TicketStatus, OrderEvent> stateMachine;

    private final Instant createdAt;
    private Instant updatedAt;

    private Order(
            OrderId id,
            UserId userId,
            EventId eventId,
            Money totalPrice,
            StateMachine<TicketStatus, OrderEvent> stateMachine
    ) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.eventId = Objects.requireNonNull(eventId);
        this.totalPrice = Objects.requireNonNull(totalPrice);
        this.stateMachine = Objects.requireNonNull(stateMachine);

        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Order create(
            UserId userId,
            EventId eventId,
            Money totalPrice,
            OrderStateMachineFactory factory
    ) {
        OrderId orderId = OrderId.newId();

        StateMachine<TicketStatus, OrderEvent> sm =
                factory.create(orderId.value());

        return new Order(
                orderId,
                userId,
                eventId,
                totalPrice,
                sm
        );
    }

    public void reserve() {
        sendEvent(OrderEvent.RESERVE_TICKET);
    }

    public void startPayment() {
        sendEvent(OrderEvent.START_PAYMENT);
    }

    public void confirmPayment() {
        sendEvent(OrderEvent.CONFIRM_PAYMENT);
    }

    public void failPayment() {
        sendEvent(OrderEvent.FAIL_PAYMENT);
    }

    public void cancel() {
        sendEvent(OrderEvent.CANCEL);
    }

    public void expireReservation() {
        sendEvent(OrderEvent.EXPIRE_RESERVATION);
    }

    private void sendEvent(OrderEvent event) {
        boolean accepted = stateMachine
                .sendEvent(org.springframework.messaging.support.MessageBuilder
                        .withPayload(event)
                        .setHeader("orderId", id.value())
                        .build());

        if (!accepted) {
            throw new IllegalStateException(
                    "Event %s not accepted from state %s"
                            .formatted(event, getStatus())
            );
        }

        this.updatedAt = Instant.now();
    }


    public OrderId getId() {
        return id;
    }

    public TicketStatus getStatus() {
        return stateMachine.getState().getId();
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isFinal() {
        return getStatus().isFinalState();
    }

    public boolean isHoldingInventory() {
        return getStatus().isHoldingInventory();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;

        Order other = (Order) o;

        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}