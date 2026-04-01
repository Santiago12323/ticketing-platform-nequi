package com.nequi.ticketing_service.infrastructure.persistence.factory;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.statemachine.machine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Currency;

@Component
public class OrderFactory {

    private final OrderStateMachineFactory smFactory;

    public OrderFactory(OrderStateMachineFactory smFactory) {
        this.smFactory = smFactory;
    }

    public Mono<Order> fromEntity(OrderEntity entity) {
        OrderId id = OrderId.of(entity.getId());
        UserId userId = UserId.of(entity.getUserId());
        EventId eventId = EventId.of(entity.getEventId());

        Money totalPrice = new Money(
                BigDecimal.valueOf(entity.getAmount()),
                Currency.getInstance(entity.getCurrency())
        );

        return smFactory.create(id.value())
                .flatMap(sm -> sm.stopReactively()
                        .then(Mono.defer(() -> {
                            sm.getStateMachineAccessor().doWithAllRegions(access ->
                                    access.resetStateMachineReactively(
                                            new DefaultStateMachineContext<>(
                                                    OrderStatus.valueOf(entity.getStatus()),
                                                    null, null, null
                                            )
                                    ).subscribe()
                            );
                            return Mono.empty();
                        }))
                        .then(sm.startReactively())
                        .thenReturn(new Order(id, userId, eventId, totalPrice, sm))
                );
    }
}
