package com.nequi.inventory.domain.port.in;

import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.RequestId;
import com.nequi.inventory.domain.valueobject.SeatId;
import reactor.core.publisher.Mono;


import java.util.Set;

public interface InventoryService {
    Mono<Void> reserve(EventId eventId, Set<SeatId> seats, RequestId requestId);
    Mono<Void> confirm(EventId eventId, Set<SeatId> seats, RequestId requestId);
    Mono<Void> release(EventId eventId, Set<SeatId> seats, RequestId requestId);
    Mono<Integer> getAvailability(EventId eventId);
}
