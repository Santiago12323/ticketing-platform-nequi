package com.nequi.ticketing_service.domain.statemachine.machine;

import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;


@Component
public class OrderStateMachineListener
        extends StateMachineListenerAdapter<OrderStatus, OrderEvent> {

    private static final Logger log =
            LoggerFactory.getLogger(OrderStateMachineListener.class);

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Override
    public void transition(Transition<OrderStatus, OrderEvent> transition) {
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
            StateMachine<OrderStatus, OrderEvent> sm,
            Exception exception) {

        log.error("State machine error for machine [{}]: {}",
                sm.getId(), exception.getMessage(), exception);
    }
}