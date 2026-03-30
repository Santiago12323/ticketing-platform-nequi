package com.nequi.ticketing_service.domain.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

/**
 * Audit listener for state machine transitions.
 * Every transition is logged for traceability — requirement from the domain:
 * "state transitions must be atomic and auditable."
 *
 * Transition logging is controlled via:
 * ticketing.statemachine.audit-enabled=true/false
 *
 * Errors are ALWAYS logged regardless of the flag.
 */
@Component
public class OrderStateMachineListener
        extends StateMachineListenerAdapter<TicketStatus, OrderEvent> {

    private static final Logger log =
            LoggerFactory.getLogger(OrderStateMachineListener.class);

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Override
    public void transition(Transition<TicketStatus, OrderEvent> transition) {
        if (!auditEnabled) return;

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
            StateMachine<TicketStatus, OrderEvent> sm,
            Exception exception) {

        log.error("State machine error for machine [{}]: {}",
                sm.getId(), exception.getMessage(), exception);
    }
}