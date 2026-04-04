package com.nequi.inventory.infrastructure.persistence.factory;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.statemachine.machine.TicketStateMachineFactory;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.TicketEntity;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TicketFactory {

    private final TicketStateMachineFactory smFactory;

    public TicketFactory(TicketStateMachineFactory smFactory) {
        this.smFactory = smFactory;
    }

    public Mono<Ticket> fromEntity(TicketEntity entity) {

        TicketId id = TicketId.of(entity.getTicketId());

        return smFactory.create(id.value())
                .flatMap(sm -> sm.stopReactively()
                        .then(Mono.defer(() -> {
                            sm.getStateMachineAccessor().doWithAllRegions(access ->
                                    access.resetStateMachineReactively(
                                            new DefaultStateMachineContext<>(
                                                    TicketStatus.valueOf(String.valueOf(entity.getStatus())),
                                                    null, null, null
                                            )
                                    ).subscribe()
                            );
                            return Mono.empty();
                        }))
                        .then(sm.startReactively())
                        .thenReturn(new Ticket(
                                id,
                                EventId.of(entity.getEventId()),
                                sm
                        ))
                );
    }
}