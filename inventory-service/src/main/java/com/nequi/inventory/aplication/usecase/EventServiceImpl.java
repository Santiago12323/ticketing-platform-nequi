package com.nequi.inventory.aplication.usecase;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.in.EventService;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.statemachine.machine.TicketStateMachineFactory;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TicketStateMachineFactory ticketStateMachineFactory;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Override
    public Mono<Void> createEvent(EventId eventId, int capacity, String name, String location) {

        return validateEventDoesNotExist(eventId)
                .then(saveEvent(buildEvent(eventId, capacity, name, location)))
                .thenMany(createTickets(eventId, capacity))
                .then()
                .doOnSuccess(v -> logIfEnabled("Event created successfully: " + eventId.value()))
                .doOnError(e -> log.error("Error creating event {}: {}", eventId.value(), e.getMessage()));
    }

    private Event buildEvent(EventId eventId, int capacity, String name, String location) {
        return new Event(
                eventId.value(),
                name,
                location,
                capacity,
                EventStatus.ACTIVE
        );
    }

    private Mono<Void> validateEventDoesNotExist(EventId eventId) {
        return eventRepository.existsById(String.valueOf(eventId))
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(
                                "EVENT_ALREADY_EXISTS",
                                "Event with id %s already exists".formatted(eventId.value())
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Event> saveEvent(Event event) {
        return eventRepository.save(event)
                .doOnSuccess(e -> logIfEnabled("Event saved: " + e.getEventId()));
    }

    private Flux<Ticket> createTickets(EventId eventId, int capacity) {
        return Flux.range(1, capacity)
                .flatMap(i -> createSingleTicket(eventId), 50)
                .doOnComplete(() -> logIfEnabled("All tickets created for event: " + eventId.value()));
    }

    private Mono<Ticket> createSingleTicket(EventId eventId) {
        TicketId ticketId = TicketId.generate();

        return Ticket.create(
                        ticketId,
                        eventId.value(),
                        ticketStateMachineFactory
                )
                .flatMap(ticketRepository::save)
                .doOnSuccess(ticket -> logIfEnabled("Ticket created: " + ticket.getTicketId().value()));
    }

    @Override
    public Mono<Event> getEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException(
                        "EVENT_NOT_FOUND",
                        "Event not found: " + eventId.value()
                )))
                .doOnSuccess(e -> logIfEnabled("Event fetched: " + eventId.value()));
    }

    @Override
    public Flux<Event> getAllEvents() {
        return eventRepository.findAll()
                .doOnComplete(() -> logIfEnabled("Fetched all events"));
    }

    @Override
    public Mono<Event> updateEvent(EventId eventId, Event updatedEvent) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException(
                        "EVENT_NOT_FOUND",
                        "Event not found: " + eventId.value()
                )))
                .flatMap(existing -> {

                    Event eventToUpdate = new Event(
                            existing.getEventId(),
                            updatedEvent.getName(),
                            updatedEvent.getLocation(),
                            updatedEvent.getTotalCapacity(),
                            EventStatus.ACTIVE
                    );

                    return eventRepository.save(eventToUpdate);
                })
                .doOnSuccess(e -> logIfEnabled("Event updated: " + eventId.value()));
    }


    @Override
    public Mono<Void> deleteEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException(
                        "EVENT_NOT_FOUND",
                        "Event not found: " + eventId.value()
                )))
                .flatMap(event -> {

                    Event cancelledEvent = new Event(
                            event.getEventId(),
                            event.getName(),
                            event.getLocation(),
                            event.getTotalCapacity(),
                            EventStatus.CANCELLED
                    );

                    return eventRepository.save(cancelledEvent);
                })
                .then()
                .doOnSuccess(v -> logIfEnabled("Event cancelled: " + eventId.value()));
    }

    private void logIfEnabled(String message) {
        if (auditEnabled) {
            log.info("[AUDIT] {}", message);
        }
    }
}