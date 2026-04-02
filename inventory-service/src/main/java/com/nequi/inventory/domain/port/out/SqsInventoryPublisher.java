package com.nequi.inventory.domain.port.out;

import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import reactor.core.publisher.Mono;

public interface SqsInventoryPublisher {
    Mono<Void> publishInventoryResponse(InventoryResponse response);
}
