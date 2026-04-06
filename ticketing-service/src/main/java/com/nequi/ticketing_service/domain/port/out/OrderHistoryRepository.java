package com.nequi.ticketing_service.domain.port.out;

import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderHistoryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderHistoryRepository {

    Mono<Void> saveHistory(String orderId, String fromStatus, String toStatus, String details);

    Flux<OrderHistoryEntity> findByOrderId(String orderId);
}
