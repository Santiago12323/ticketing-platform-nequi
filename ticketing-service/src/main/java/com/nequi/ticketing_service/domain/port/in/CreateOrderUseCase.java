package com.nequi.ticketing_service.domain.port.in;

import com.nequi.ticketing_service.domain.valueobject.*;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CreateOrderUseCase {
    Mono<OrderId> execute(UserId userId,
                          EventId eventId,
                          Money totalPrice,
                          List<String> seatIds);
}
