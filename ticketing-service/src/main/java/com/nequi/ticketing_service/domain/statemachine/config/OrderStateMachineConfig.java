package com.nequi.ticketing_service.domain.statemachine.config;

import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.statemachine.machine.OrderStateMachineListener;
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
public class OrderStateMachineConfig
        extends StateMachineConfigurerAdapter<OrderStatus, OrderEvent> {

    private final OrderStateMachineListener listener;

    public OrderStateMachineConfig(OrderStateMachineListener listener) {
        this.listener = listener;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderStatus, OrderEvent> config) throws Exception {
        config.withConfiguration()
                .autoStartup(false)
                .listener(listener);
    }

    @Override
    public void configure(StateMachineStateConfigurer<OrderStatus, OrderEvent> states) throws Exception {
        states.withStates()
                .initial(OrderStatus.PENDING_VALIDATION)
                .end(OrderStatus.PAID)
                .end(OrderStatus.CANCELLED)
                .end(OrderStatus.EXPIRED)
                .end(OrderStatus.FAILED_VALIDATION)
                .states(EnumSet.allOf(OrderStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStatus, OrderEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.PENDING_PAYMENT)
                .event(OrderEvent.VALIDATION_SUCCESS)
                .and()
                .withExternal()
                .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.FAILED_VALIDATION)
                .event(OrderEvent.VALIDATION_FAILED)
                .and()

                .withExternal()
                .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PAID)
                .event(OrderEvent.CONFIRM_PAYMENT)
                .and()
                .withExternal()
                .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CANCELLED)
                .event(OrderEvent.CANCEL)
                .and()
                .withExternal()
                .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.EXPIRED)
                .event(OrderEvent.EXPIRE);

    }
}