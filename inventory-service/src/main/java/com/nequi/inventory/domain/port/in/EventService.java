package com.nequi.inventory.domain.port.in;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.valueobject.EventId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventService {
    Mono<Void> createEvent(EventId eventId, int capacity, String name, String location);

    Mono<Event> getEvent(EventId eventId);

    Flux<Event> getAllEvents();

    Mono<Event> updateEvent(EventId eventId, Event updatedEvent);

    Mono<Void> deleteEvent(EventId eventId);
}