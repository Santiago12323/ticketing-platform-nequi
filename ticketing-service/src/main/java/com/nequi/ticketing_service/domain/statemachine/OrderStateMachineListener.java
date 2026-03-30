package com.nequi.ticketing_service.domain.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

/**
 * Audit listener for state machine transitions.
 * Every transition is logged for traceability — requirement from the domain:
 * "state transitions must be atomic and auditable."
 */
@Component
public class OrderStateMachineListener
        extends StateMachineListenerAdapter<TicketStatus, OrderEvent> {

    private static final Logger log =
            LoggerFactory.getLogger(OrderStateMachineListener.class);

    @Override
    public void transition(Transition<TicketStatus, OrderEvent> transition) {
        if (transition.getSource() != null && transition.getTarget() != null) {
            log.info("Order state transition: [{}] --({})--> [{}]",
                    transition.getSource().getId(),
                    transition.getTrigger() != null
                            ? transition.getTrigger().getEvent()
                            : "N/A",
                    transition.getTarget().getId());
        }
    }

    @Override
    public void stateMachineError(
            org.springframework.statemachine.StateMachine<TicketStatus, OrderEvent> sm,
            Exception exception) {

        log.error("State machine error for machine [{}]: {}",
                sm.getId(), exception.getMessage(), exception);
    }
}