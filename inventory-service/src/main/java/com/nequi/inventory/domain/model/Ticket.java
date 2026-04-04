package com.nequi.inventory.domain.model;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.statemachine.TicketEvent;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.statemachine.machine.TicketStateMachineFactory;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Getter
@Setter
@EqualsAndHashCode(of = "ticketId")
@RequiredArgsConstructor
public class Ticket {

    private final TicketId ticketId;
    private final EventId eventId;

    private final StateMachine<TicketStatus, TicketEvent> stateMachine;

    private TicketStatus status;
    private String userId;
    private String orderId;
    private String expiresAt;
    private Long version;
    private String createdAt;
    private String updatedAt;

    private final Instant createdAtInstant = Instant.now();
    private Instant updatedAtInstant = createdAtInstant;

    public static Mono<Ticket> create(
            TicketId ticketId,
            EventId eventId,
            TicketStateMachineFactory factory
    ) {
        return factory.create(ticketId.value())
                .map(sm -> {
                    Ticket ticket = new Ticket(ticketId, eventId, sm);
                    ticket.setStatus(sm.getState().getId());
                    ticket.setCreatedAt(ticket.createdAtInstant.toString());
                    ticket.setUpdatedAt(ticket.updatedAtInstant.toString());
                    return ticket;
                });
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

    public Mono<Ticket> failPayment() {
        return sendEvent(TicketEvent.FAIL_PAYMENT);
    }

    public Mono<Ticket> assignComplimentary(Event event) {
        validateEvent(event);
        return sendEvent(TicketEvent.ASSIGN_COMPLIMENTARY);
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

                    this.updatedAtInstant = Instant.now();
                    this.updatedAt = updatedAtInstant.toString();
                    this.status = stateMachine.getState().getId();
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
}
