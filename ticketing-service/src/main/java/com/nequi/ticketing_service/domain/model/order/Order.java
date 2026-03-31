package com.nequi.ticketing_service.domain.model.order;

import com.nequi.ticketing_service.domain.exception.BusinessException;
import com.nequi.ticketing_service.domain.exception.ErrorCode;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.statemachine.TicketStatus;
import com.nequi.ticketing_service.domain.valueobject.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Getter
@EqualsAndHashCode(of = "id")
@RequiredArgsConstructor
public class Order {

    private final OrderId id;
    private final UserId userId;
    private final EventId eventId;
    private final Money totalPrice;
    private final StateMachine<TicketStatus, OrderEvent> stateMachine;

    private final Instant createdAt = Instant.now();
    private Instant updatedAt = createdAt;

    public static Mono<Order> create(UserId userId,
                                     EventId eventId,
                                     Money totalPrice,
                                     OrderStateMachineFactory factory) {
        OrderId orderId = OrderId.newId();
        log.info("Creating Order with id={}, userId={}, eventId={}, totalPrice={}",
                orderId.value(), userId.value(), eventId.value(), totalPrice.amount());

        return factory.create(orderId.value())
                .doOnSubscribe(sub -> log.debug("[{}] Starting StateMachine...", orderId.value()))
                .doOnSuccess(sm -> log.info("[{}] StateMachine started successfully", orderId.value()))
                .doOnError(err -> log.error("[{}] Error starting StateMachine", orderId.value(), err))
                .map(sm -> new Order(orderId, userId, eventId, totalPrice, sm));
    }

    // --- Domain transitions (reactive) ---
    public Mono<Order> reserve() { return sendEvent(OrderEvent.RESERVE_TICKET); }
    public Mono<Order> startPayment() { return sendEvent(OrderEvent.START_PAYMENT); }
    public Mono<Order> confirmPayment() { return sendEvent(OrderEvent.CONFIRM_PAYMENT); }
    public Mono<Order> failPayment() { return sendEvent(OrderEvent.FAIL_PAYMENT); }
    public Mono<Order> cancel() { return sendEvent(OrderEvent.CANCEL); }
    public Mono<Order> expireReservation() { return sendEvent(OrderEvent.EXPIRE_RESERVATION); }

    private Mono<Order> sendEvent(OrderEvent event) {
        log.info("[{}] Sending event {}", id.value(), event);

        return stateMachine.sendEvent(Mono.just(
                        org.springframework.messaging.support.MessageBuilder
                                .withPayload(event)
                                .setHeader("orderId", id.value())
                                .build()))
                .next()
                .doOnSubscribe(sub -> log.debug("[{}] Subscribed to event {}", id.value(), event))
                .doOnError(err -> log.error("[{}] Error sending event {}", id.value(), event, err))
                .flatMap(result -> {
                    log.debug("[{}] Event {} result={}", id.value(), event, result.getResultType());
                    if (result.getResultType() == org.springframework.statemachine.StateMachineEventResult.ResultType.DENIED) {
                        log.warn("[{}] Event {} denied from state {}", id.value(), event, getStatus());
                        return Mono.error(new BusinessException(
                                ErrorCode.ORDER_EVENT_NOT_ACCEPTED.code(),
                                "Event %s not accepted from state %s".formatted(event, getStatus())
                        ));
                    }
                    this.updatedAt = Instant.now();
                    log.info("[{}] Event {} applied successfully, new status={}", id.value(), event, getStatus());
                    return Mono.just(this);
                });
    }

    public TicketStatus getStatus() {
        return stateMachine.getState().getId();
    }

    public boolean isFinal() { return getStatus().isFinalState(); }
    public boolean isHoldingInventory() { return getStatus().isHoldingInventory(); }
}
