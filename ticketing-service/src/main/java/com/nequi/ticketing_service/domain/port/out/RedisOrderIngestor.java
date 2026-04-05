package com.nequi.ticketing_service.domain.port.out;

import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.valueobject.*;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RedisOrderIngestor {
    Mono<OrderId> ingest(UserId userId, EventId eventId, Money money, List<String> seats, OrderId orderId);
}
