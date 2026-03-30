package com.nequi.ticketing_service.domain.statemachine;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

/**
 * Factory wrapper for StateMachine instances.
 *
 * Each order has its own independent state machine.
 * This class is responsible for:
 * - Creating new machines
 * - Restoring machines from persisted state
 *
 * Designed for async/event-driven systems (WebFlux, SQS, Kafka).
 */
@Component
public class OrderStateMachineFactory {

    private final StateMachineFactory<TicketStatus, OrderEvent> factory;

    public OrderStateMachineFactory(
            StateMachineFactory<TicketStatus, OrderEvent> factory) {
        this.factory = factory;
    }

    /**
     * Creates a fresh state machine for a new order.
     */
    public StateMachine<TicketStatus, OrderEvent> create(String orderId) {
        StateMachine<TicketStatus, OrderEvent> sm = factory.getStateMachine(orderId);

        sm.startReactively().block();

        return sm;
    }

    /**
     * Restores a state machine from a persisted state.
     */
    public StateMachine<TicketStatus, OrderEvent> restore(
            String orderId, TicketStatus currentStatus) {

        StateMachine<TicketStatus, OrderEvent> sm = factory.getStateMachine(orderId);

        sm.stopReactively().block();

        sm.getStateMachineAccessor()
                .doWithAllRegions(accessor ->
                        accessor.resetStateMachineReactively(
                                new DefaultStateMachineContext<>(
                                        currentStatus,
                                        null,
                                        null,
                                        null
                                )
                        ).block()
                );

        sm.startReactively().block();

        return sm;
    }
}