package com.nequi.ticketing_service.domain.port.in;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.valueobject.*;
import reactor.core.publisher.Mono;

import java.util.List;

public interface OrderUseCase {
    Mono<OrderId> create(UserId userId, EventId eventId, Money totalPrice, List<String> seatIds);

    Mono<OrderId> confirmAll(UserId userId, EventId eventId, Money totalPrice,
                             List<String> seatIds, OrderId orderId);

    Mono<Order> getById(OrderId id);

    Mono<Void> expireOrder(OrderId orderId);
}
