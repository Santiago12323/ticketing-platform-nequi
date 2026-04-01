package com.nequi.ticketing_service.domain.port.out;

import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.Money;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.UserId;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RedisOrderIngestor {
    Mono<OrderId> ingest(UserId userId, EventId eventId, Money money, List<String> seats, OrderId orderId);
}
