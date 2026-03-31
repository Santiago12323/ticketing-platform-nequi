package com.nequi.ticketing_service.domain.port.out;

import com.nequi.ticketing_service.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;

public interface OrderPublisher {
    Mono<Void> publishOrderCreated(OrderId orderId);
}
