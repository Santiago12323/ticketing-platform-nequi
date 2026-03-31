package com.nequi.inventory.infrastructure.web.handler;

import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.RequestId;
import com.nequi.inventory.domain.valueobject.SeatId;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
public class EventHandler {

    private final InventoryService inventoryService;

    public EventHandler(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public Mono<ServerResponse> getAvailability(ServerRequest request) {
        EventId eventId = new EventId(request.pathVariable("id"));
        return inventoryService.getAvailability(eventId)
                .flatMap(count -> ServerResponse.ok().bodyValue(count));
    }

    public Mono<ServerResponse> reserve(ServerRequest request) {
        EventId eventId = new EventId(request.pathVariable("id"));
        RequestId requestId = new RequestId(request.headers().firstHeader("X-Request-Id"));
        return request.bodyToFlux(String.class)
                .map(SeatId::new)
                .collect(Collectors.toSet())
                .flatMap(seats -> inventoryService.reserve(eventId, seats, requestId))
                .then(ServerResponse.ok().build());
    }
}
