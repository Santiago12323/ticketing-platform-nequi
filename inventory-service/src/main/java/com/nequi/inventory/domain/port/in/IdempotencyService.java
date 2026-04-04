package com.nequi.inventory.domain.port.in;

import com.nequi.inventory.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;

public interface IdempotencyService {
    Mono<Boolean> tryProcess(OrderId orderId);
}