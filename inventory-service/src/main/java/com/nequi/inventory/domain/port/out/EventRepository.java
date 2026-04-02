package com.nequi.inventory.domain.port.out;


import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.valueobject.EventId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventRepository {
    Mono<Event> findById(EventId id);
    Mono<Event> save(Event event);

    Mono<Boolean> existsById(String eventId);

    Flux<Event> findAll();
}
