package com.nequi.ticketing_service.domain.port.out;


import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;

public interface OrderRepository {
    Mono<Order> save(Order order, java.util.List<String> seatIds);
    Mono<Order> findById(OrderId id);
}
