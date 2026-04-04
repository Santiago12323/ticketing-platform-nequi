package com.nequi.ticketing_service.domain.port.in;

import com.nequi.ticketing_service.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;

public interface ProcessInventoryResponseUseCase {
    Mono<Void> execute(OrderId orderId, boolean isSuccess);
}
