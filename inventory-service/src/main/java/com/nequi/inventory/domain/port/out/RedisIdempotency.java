package com.nequi.inventory.domain.port.out;

import com.nequi.inventory.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;

public interface RedisIdempotency {
    Mono<Boolean> exists(OrderId orderId);

    Mono<Void> save(OrderId orderId);
}
