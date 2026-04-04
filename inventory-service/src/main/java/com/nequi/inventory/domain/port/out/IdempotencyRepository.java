package com.nequi.inventory.domain.port.out;

import com.nequi.inventory.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;

public interface IdempotencyRepository {
    Mono<Boolean> saveIfNotExists(OrderId orderId);

    Mono<Boolean> exists(OrderId orderId);
}
