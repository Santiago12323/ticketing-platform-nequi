package com.nequi.ticketing_service.domain.port.out;

import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;

import java.util.List;

public interface OrderPublisher {

    Mono<Boolean> publishInventoryCheck(OrderId orderId, EventId eventId, List<String> seatIds);

    Mono<Void> publishOrderCreated(OrderId orderId);
}
