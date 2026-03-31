package com.nequi.ticketing_service.domain.statemachine;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OrderStateMachineFactory {

    private final StateMachineFactory<TicketStatus, OrderEvent> factory;

    public OrderStateMachineFactory(StateMachineFactory<TicketStatus, OrderEvent> factory) {
        this.factory = factory;
    }

    public Mono<StateMachine<TicketStatus, OrderEvent>> create(String orderId) {
        StateMachine<TicketStatus, OrderEvent> sm = factory.getStateMachine(orderId);
        return sm.startReactively().thenReturn(sm);
    }

    public Mono<StateMachine<TicketStatus, OrderEvent>> restore(String orderId, TicketStatus currentStatus) {
        StateMachine<TicketStatus, OrderEvent> sm = factory.getStateMachine(orderId);

        return sm.stopReactively()
                .then(Mono.defer(() -> {
                    sm.getStateMachineAccessor().doWithAllRegions(accessor ->
                            accessor.resetStateMachineReactively(
                                    new DefaultStateMachineContext<>(currentStatus, null, null, null)
                            ).subscribe()
                    );
                    return Mono.empty();
                }))
                .then(sm.startReactively())
                .thenReturn(sm);
    }
}
