package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.in.CreateOrderUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.valueobject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCaseImpl implements CreateOrderUseCase {

    private final OrderRepository repository;
    private final OrderPublisher publisher;
    private final OrderStateMachineFactory smFactory;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Override
    public Mono<OrderId> execute(UserId userId,
                                 EventId eventId,
                                 Money totalPrice,
                                 List<String> seatIds) {

        return Order.create(userId, eventId, totalPrice, smFactory)
                .flatMap(order ->
                        order.reserve()
                                .flatMap(initializedOrder ->
                                        repository.save(initializedOrder, seatIds)
                                                .flatMap(savedOrder ->
                                                        publisher.publishInventoryCheck(savedOrder.getId(), eventId, seatIds)
                                                                .thenReturn(savedOrder.getId())
                                                )
                                )
                )
                .doOnSuccess(id -> {
                    if (auditEnabled) {
                        log.info("Order {} created and queued for inventory check for User: {}",
                                id.value(), userId.value());
                    }
                })
                .doOnError(err -> log.error("Failed to create order for User: {} and Event: {}. Error: {}",
                        userId.value(), eventId.value(), err.getMessage()));
    }
}