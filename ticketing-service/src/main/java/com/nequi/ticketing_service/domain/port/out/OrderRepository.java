package com.nequi.ticketing_service.domain.port.out;


import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.TicketId;
import reactor.core.publisher.Mono;

import java.util.List;

public interface OrderRepository {
    Mono<Order> save(Order order);

    Mono<Order> updateStatus(Order order);

    Mono<Order> findById(OrderId id);
}
