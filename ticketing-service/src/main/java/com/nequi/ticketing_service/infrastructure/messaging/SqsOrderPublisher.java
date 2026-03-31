package com.nequi.ticketing_service.infrastructure.messaging;

import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;

@Component
public class SqsOrderPublisher implements OrderPublisher {

    // Aquí inyectas el SQS client

    @Override
    public Mono<Void> publishOrderCreated(OrderId orderId) {
        return Mono.fromRunnable(() -> {
            // Enviar mensaje JSON con orderId a la cola SQS
        });
    }
}
