package com.nequi.ticketing_service.domain.port.out;

import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderHistoryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderHistoryService {
    Mono<Void> recordTimestamp(OrderId orderId, OrderStatus from, OrderStatus to, String details);

    Flux<OrderHistoryEntity> getHistory(OrderId orderId);
}
