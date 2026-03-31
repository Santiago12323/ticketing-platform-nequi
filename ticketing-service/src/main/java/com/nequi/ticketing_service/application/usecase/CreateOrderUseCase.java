package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.model.order.Order;

import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.valueobject.*;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class CreateOrderUseCase {

    private final OrderRepository repository;
    private final OrderPublisher publisher;
    private final OrderStateMachineFactory smFactory;

    @Autowired
    public CreateOrderUseCase(OrderRepository repository,
                              OrderPublisher publisher,
                              OrderStateMachineFactory smFactory) {
        this.repository = repository;
        this.publisher = publisher;
        this.smFactory = smFactory;
    }

    public Mono<OrderId> execute(UserId userId,
                                 EventId eventId,
                                 Money totalPrice,
                                 List<String> seatIds) {

        // 1. Crear orden en dominio
        Order order = Order.create(userId, eventId, totalPrice, smFactory);

        // 2. Reservar entradas
        order.reserve();

        // 3. Persistir y publicar
        return repository.save(order, seatIds)
                .flatMap(saved -> publisher.publishOrderCreated(saved.getId())
                        .thenReturn(saved.getId()));
    }
}
