package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.in.CreateOrderUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.valueobject.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class CreateOrderUseCaseImpl implements CreateOrderUseCase {

    private final OrderRepository repository;
    private final OrderPublisher publisher;
    private final OrderStateMachineFactory smFactory;

    @Autowired
    public CreateOrderUseCaseImpl(OrderRepository repository,
                                  OrderPublisher publisher,
                                  OrderStateMachineFactory smFactory) {
        this.repository = repository;
        this.publisher = publisher;
        this.smFactory = smFactory;
    }

    @Override
    public Mono<OrderId> execute(UserId userId,
                                 EventId eventId,
                                 Money totalPrice,
                                 List<String> seatIds) {
        Order order = Order.create(userId, eventId, totalPrice, smFactory);
        order.reserve();

        return repository.save(order, seatIds)
                .flatMap(saved -> publisher.publishOrderCreated(saved.getId())
                        .thenReturn(saved.getId()));
    }
}
