package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.in.CreateOrderUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.valueobject.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateOrderUseCaseImpl implements CreateOrderUseCase {

    private final OrderRepository repository;
    private final OrderPublisher publisher;
    private final OrderStateMachineFactory smFactory;

    @Override
    public Mono<OrderId> execute(UserId userId,
                                 EventId eventId,
                                 Money totalPrice,
                                 List<String> seatIds) {

        return Order.create(userId, eventId, totalPrice, smFactory)
                .flatMap(order ->
                        order.reserve()
                                .then(publisher.publishInventoryCheck(order.getId(), eventId, seatIds))
                                .flatMap(hasInventory -> {
                                    if (hasInventory) {
                                        return repository.save(order, seatIds)
                                                .flatMap(saved -> saved.startPayment()
                                                        .then(publisher.publishOrderCreated(saved.getId()))
                                                        .thenReturn(saved.getId()));
                                    } else {
                                        return order.cancel()
                                                .then(repository.save(order, seatIds))
                                                .thenReturn(order.getId());
                                    }
                                })
                );
    }
}
