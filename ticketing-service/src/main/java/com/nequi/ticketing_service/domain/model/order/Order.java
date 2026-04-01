package com.nequi.ticketing_service.domain.model.order;

import com.nequi.ticketing_service.domain.exception.BusinessException;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.machine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Getter
@EqualsAndHashCode(of = "id")
@RequiredArgsConstructor
public class Order {

    private final OrderId id;
    private final UserId userId;
    private final EventId eventId;
    private final Money totalPrice;
    private final StateMachine<OrderStatus, OrderEvent> stateMachine;

    private final Instant createdAt = Instant.now();
    private Instant updatedAt = createdAt;

    public static Mono<Order> create(OrderId orderId,
                                     UserId userId,
                                     EventId eventId,
                                     Money totalPrice,
                                     OrderStateMachineFactory factory) {

        return factory.create(orderId.value())
                .map(sm -> new Order(orderId, userId, eventId, totalPrice, sm));
    }

    public Mono<Order> confirmInventory() {
        return sendEvent(OrderEvent.VALIDATION_SUCCESS);
    }

    public Mono<Order> failInventory() {
        return sendEvent(OrderEvent.VALIDATION_FAILED);
    }

    public Mono<Order> startPayment() {
        return sendEvent(OrderEvent.START_PAYMENT);
    }

    public Mono<Order> pay() {
        return sendEvent(OrderEvent.CONFIRM_PAYMENT);
    }

    public Mono<Order> cancel() {
        return sendEvent(OrderEvent.CANCEL);
    }

    public Mono<Order> expire() {
        return sendEvent(OrderEvent.EXPIRE);
    }

    private Mono<Order> sendEvent(OrderEvent event) {
        return stateMachine.sendEvent(Mono.just(
                        MessageBuilder.withPayload(event)
                                .setHeader("orderId", id.value())
                                .build()))
                .next()
                .flatMap(result -> {
                    if (result.getResultType() == StateMachineEventResult.ResultType.DENIED) {
                        return Mono.error(new BusinessException(
                                "ORDER_EVENT_NOT_ACCEPTED",
                                "El evento %s no es válido para el estado actual %s".formatted(event, getStatus())
                        ));
                    }
                    this.updatedAt = Instant.now();
                    return Mono.just(this);
                });
    }

    public OrderStatus getStatus() {
        return stateMachine.getState().getId();
    }

    public boolean isFinal() {
        return getStatus().isFinalState();
    }
}