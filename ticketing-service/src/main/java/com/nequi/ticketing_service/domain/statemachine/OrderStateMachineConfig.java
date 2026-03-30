package com.nequi.ticketing_service.domain.statemachine;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * State Machine for Ticket lifecycle.
 *
 * Improvements:
 * - No ambiguous events (START vs CONFIRM_PAYMENT)
 * - Explicit cancel flow
 * - Async-friendly design (ready for WebFlux + messaging)
 * - Listener attached for auditing
 */
@Configuration
@EnableStateMachineFactory
public class OrderStateMachineConfig
        extends StateMachineConfigurerAdapter<TicketStatus, OrderEvent> {

    private final OrderStateMachineListener listener;

    public OrderStateMachineConfig(OrderStateMachineListener listener) {
        this.listener = listener;
    }

    /**
     * GENERAL CONFIG (listener, startup behavior)
     */
    @Override
    public void configure(
            StateMachineConfigurationConfigurer<TicketStatus, OrderEvent> config)
            throws Exception {

        config
                .withConfiguration()
                .autoStartup(true)
                .listener(listener);
    }

    /**
     * STATES
     */
    @Override
    public void configure(
            StateMachineStateConfigurer<TicketStatus, OrderEvent> states)
            throws Exception {

        states
                .withStates()
                .initial(TicketStatus.AVAILABLE)
                .end(TicketStatus.SOLD)
                .end(TicketStatus.COMPLIMENTARY)
                .states(EnumSet.allOf(TicketStatus.class));
    }

    /**
     * TRANSITIONS
     */
    @Override
    public void configure(
            StateMachineTransitionConfigurer<TicketStatus, OrderEvent> transitions)
            throws Exception {

        transitions

                // AVAILABLE → RESERVED
                .withExternal()
                .source(TicketStatus.AVAILABLE)
                .target(TicketStatus.RESERVED)
                .event(OrderEvent.RESERVE_TICKET)
                .and()

                // AVAILABLE → COMPLIMENTARY
                .withExternal()
                .source(TicketStatus.AVAILABLE)
                .target(TicketStatus.COMPLIMENTARY)
                .event(OrderEvent.ASSIGN_COMPLIMENTARY)
                .and()

                // RESERVED → PROCESSING_PAYMENT
                .withExternal()
                .source(TicketStatus.RESERVED)
                .target(TicketStatus.PROCESSING_PAYMENT)
                .event(OrderEvent.START_PAYMENT)
                .and()

                // RESERVED → AVAILABLE (expiration)
                .withExternal()
                .source(TicketStatus.RESERVED)
                .target(TicketStatus.AVAILABLE)
                .event(OrderEvent.EXPIRE_RESERVATION)
                .and()

                // RESERVED → AVAILABLE (manual cancel)
                .withExternal()
                .source(TicketStatus.RESERVED)
                .target(TicketStatus.AVAILABLE)
                .event(OrderEvent.CANCEL)
                .and()

                // PROCESSING_PAYMENT → SOLD
                .withExternal()
                .source(TicketStatus.PROCESSING_PAYMENT)
                .target(TicketStatus.SOLD)
                .event(OrderEvent.CONFIRM_PAYMENT)
                .and()

                // PROCESSING_PAYMENT → AVAILABLE (fail)
                .withExternal()
                .source(TicketStatus.PROCESSING_PAYMENT)
                .target(TicketStatus.AVAILABLE)
                .event(OrderEvent.FAIL_PAYMENT);
    }
}