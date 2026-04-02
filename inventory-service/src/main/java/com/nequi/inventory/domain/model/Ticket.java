package com.nequi.inventory.domain.model;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.statemachine.TicketEvent;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Getter
@EqualsAndHashCode(of = "ticketId")
@RequiredArgsConstructor
public class Ticket {

    private final TicketId ticketId;
    private final String eventId;

    private final StateMachine<TicketStatus, TicketEvent> stateMachine;

    private final Instant createdAt = Instant.now();
    private Instant updatedAt = createdAt;

    public static Mono<Ticket> create(
            TicketId ticketId,
            String eventId,
            com.nequi.inventory.domain.statemachine.machine.TicketStateMachineFactory factory
    ) {
        return factory.create(ticketId.value())
                .map(sm -> new Ticket(ticketId, eventId, sm));
    }

    public Mono<Ticket> reserve(Event event) {
        validateEvent(event);
        return sendEvent(TicketEvent.RESERVE);
    }

    public Mono<Ticket> startPayment(Event event) {
        validateEvent(event);
        return sendEvent(TicketEvent.START_PAYMENT);
    }

    public Mono<Ticket> confirmPayment() {
        return sendEvent(TicketEvent.CONFIRM_PAYMENT);
    }

    public Mono<Ticket> cancel() {
        return sendEvent(TicketEvent.CANCEL_RESERVATION);
    }

    public Mono<Ticket> expire() {
        return sendEvent(TicketEvent.EXPIRE_RESERVATION);
    }


    private Mono<Ticket> sendEvent(TicketEvent event) {

        return stateMachine.sendEvent(Mono.just(
                        MessageBuilder.withPayload(event)
                                .setHeader("ticketId", ticketId.value())
                                .build()))
                .next()
                .flatMap(result -> {
                    if (result.getResultType() == StateMachineEventResult.ResultType.DENIED) {
                        return Mono.error(new BusinessException(
                                "TICKET_EVENT_NOT_ACCEPTED",
                                "Evento %s no válido para estado %s"
                                        .formatted(event, getStatus())
                        ));
                    }

                    this.updatedAt = Instant.now();
                    return Mono.just(this);
                });
    }

    private void validateEvent(Event event) {
        if (event == null) {
            throw new BusinessException("EVENT_NOT_FOUND", "Event does not exist");
        }

        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new BusinessException("EVENT_NOT_ACTIVE", "Event is not available");
        }
    }


    public TicketStatus getStatus() {
        return stateMachine.getState().getId();
    }

    public boolean isFinal() {
        return getStatus().isFinalState();
    }

    public Mono<Ticket> failPayment() {
        return sendEvent(TicketEvent.FAIL_PAYMENT);
    }

    public Mono<Ticket> assignComplimentary(Event event) {
        validateEvent(event);
        return sendEvent(TicketEvent.ASSIGN_COMPLIMENTARY);
    }
}