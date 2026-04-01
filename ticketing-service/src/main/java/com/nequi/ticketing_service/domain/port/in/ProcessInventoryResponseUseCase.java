package com.nequi.ticketing_service.domain.port.in;

import reactor.core.publisher.Mono;

public interface ProcessInventoryResponseUseCase {
    Mono<Void> execute(String orderId, boolean isSuccess);
}
