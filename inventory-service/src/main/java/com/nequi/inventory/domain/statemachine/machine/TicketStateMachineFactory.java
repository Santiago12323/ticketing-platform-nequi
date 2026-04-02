package com.nequi.inventory.domain.statemachine.machine;

import com.nequi.inventory.domain.statemachine.TicketEvent;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TicketStateMachineFactory {

    private final StateMachineFactory<TicketStatus, TicketEvent> factory;

    public TicketStateMachineFactory(StateMachineFactory<TicketStatus, TicketEvent> factory) {
        this.factory = factory;
    }

    public Mono<StateMachine<TicketStatus, TicketEvent>> create(String ticketId) {
        StateMachine<TicketStatus, TicketEvent> sm = factory.getStateMachine(ticketId);

        return sm.startReactively()
                .thenReturn(sm);
    }

    public Mono<StateMachine<TicketStatus, TicketEvent>> restore(String ticketId, TicketStatus currentStatus) {

        StateMachine<TicketStatus, TicketEvent> sm = factory.getStateMachine(ticketId);

        return sm.stopReactively()
                .then(
                        sm.getStateMachineAccessor()
                                .withRegion()
                                .resetStateMachineReactively(
                                        new DefaultStateMachineContext<>(
                                                currentStatus,
                                                null,
                                                null,
                                                null
                                        )
                                )
                )
                .then(sm.startReactively())
                .thenReturn(sm);
    }
}