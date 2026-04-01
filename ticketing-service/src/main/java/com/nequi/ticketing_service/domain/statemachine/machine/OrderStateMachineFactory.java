package com.nequi.ticketing_service.domain.statemachine.machine;

import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OrderStateMachineFactory {

    private final StateMachineFactory<OrderStatus, OrderEvent> factory;

    public OrderStateMachineFactory(StateMachineFactory<OrderStatus, OrderEvent> factory) {
        this.factory = factory;
    }

    public Mono<StateMachine<OrderStatus, OrderEvent>> create(String orderId) {
        StateMachine<OrderStatus, OrderEvent> sm = factory.getStateMachine(orderId);
        return sm.startReactively().thenReturn(sm);
    }

    public Mono<StateMachine<OrderStatus, OrderEvent>> restore(String orderId, OrderStatus currentStatus) {
        StateMachine<OrderStatus, OrderEvent> sm = factory.getStateMachine(orderId);

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
