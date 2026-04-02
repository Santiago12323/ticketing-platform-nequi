package com.nequi.inventory.domain.statemachine.config;

import com.nequi.inventory.domain.statemachine.TicketEvent;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.statemachine.machine.TicketStateMachineListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Slf4j
@Configuration
@EnableStateMachineFactory
public class TicketStateMachineConfig
        extends StateMachineConfigurerAdapter<TicketStatus, TicketEvent> {

    private final TicketStateMachineListener listener;

    public TicketStateMachineConfig(TicketStateMachineListener listener) {
        this.listener = listener;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<TicketStatus, TicketEvent> config) throws Exception {
        config.withConfiguration()
                .autoStartup(false)
                .listener(listener);
    }

    @Override
    public void configure(StateMachineStateConfigurer<TicketStatus, TicketEvent> states) throws Exception {
        states.withStates()
                .initial(TicketStatus.AVAILABLE)
                .end(TicketStatus.SOLD)
                .end(TicketStatus.COMPLIMENTARY)
                .states(EnumSet.allOf(TicketStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<TicketStatus, TicketEvent> transitions) throws Exception {

        transitions

                .withExternal()
                .source(TicketStatus.AVAILABLE).target(TicketStatus.RESERVED)
                .event(TicketEvent.RESERVE)
                .and()

                .withExternal()
                .source(TicketStatus.AVAILABLE).target(TicketStatus.COMPLIMENTARY)
                .event(TicketEvent.ASSIGN_COMPLIMENTARY)
                .and()

                .withExternal()
                .source(TicketStatus.RESERVED).target(TicketStatus.PENDING_CONFIRMATION)
                .event(TicketEvent.START_PAYMENT)
                .and()

                .withExternal()
                .source(TicketStatus.RESERVED).target(TicketStatus.AVAILABLE)
                .event(TicketEvent.EXPIRE_RESERVATION)
                .and()
                .withExternal()
                .source(TicketStatus.RESERVED).target(TicketStatus.AVAILABLE)
                .event(TicketEvent.CANCEL_RESERVATION)
                .and()

                .withExternal()
                .source(TicketStatus.PENDING_CONFIRMATION).target(TicketStatus.SOLD)
                .event(TicketEvent.CONFIRM_PAYMENT)
                .and()

                .withExternal()
                .source(TicketStatus.PENDING_CONFIRMATION).target(TicketStatus.AVAILABLE)
                .event(TicketEvent.FAIL_PAYMENT)
                .and()
                .withExternal()
                .source(TicketStatus.PENDING_CONFIRMATION).target(TicketStatus.AVAILABLE)
                .event(TicketEvent.EXPIRE_RESERVATION);
    }
}