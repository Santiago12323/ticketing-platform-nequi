package com.nequi.inventory.application.usecase;

import com.nequi.inventory.aplication.usecase.EventServiceImpl;
import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private EventServiceImpl eventService;

    private EventId eventId;
    private final String VALID_UUID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        eventId = new EventId(VALID_UUID);
    }

    @Test
    @DisplayName("Should create event and its tickets successfully")
    void shouldCreateEventSuccessfully() {
        EventId id = EventId.newId();
        // Usamos el método estático create del dominio tal como lo hace el servicio
        Ticket realTicket = Ticket.create(TicketId.generate(), id);

        when(eventRepository.existsById(anyString())).thenReturn(Mono.just(false));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(Mono.just(realTicket));

        StepVerifier.create(eventService.createEvent(id, 3, "Concert", "Bogota"))
                .verifyComplete();

        verify(eventRepository).save(any(Event.class));
        verify(ticketRepository, times(3)).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Should trigger BusinessException when event ID is already taken")
    void shouldTriggerExceptionWhenEventIdIsTaken() {
        EventId id = EventId.newId();
        when(eventRepository.existsById(id.value())).thenReturn(Mono.just(true));

        StepVerifier.create(eventService.createEvent(id, 10, "Festival", "Medellín"))
                .expectErrorMatches(throwable -> throwable instanceof BusinessException &&
                        ((BusinessException) throwable).getErrorCode().equals("EVENT_ALREADY_EXISTS"))
                .verify();

        verify(eventRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should create event but zero tickets when capacity is 0")
    void shouldCreateEventWithZeroTickets() {
        EventId id = EventId.newId();
        when(eventRepository.existsById(id.value())).thenReturn(Mono.just(false));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(eventService.createEvent(id, 0, "Empty Event", "Virtual"))
                .verifyComplete();

        verify(eventRepository).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when event repository throws an infrastructure error")
    void shouldFailWhenEventRepositoryFails() {
        EventId id = EventId.newId();
        when(eventRepository.existsById(anyString())).thenReturn(Mono.just(false));
        when(eventRepository.save(any())).thenReturn(Mono.error(new RuntimeException("Database Error")));

        StepVerifier.create(eventService.createEvent(id, 2, "Test", "Loc"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("Should return event when it exists")
    void shouldReturnEventWhenFound() {
        Event mockEvent = new Event(eventId, "Name", "Loc", 10, EventStatus.ACTIVE);
        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));

        StepVerifier.create(eventService.getEvent(eventId))
                .expectNext(mockEvent)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw BusinessException when event is not found")
    void shouldFailWhenEventNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Mono.empty());

        StepVerifier.create(eventService.getEvent(eventId))
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_FOUND"))
                .verify();
    }

    @Test
    @DisplayName("Should return all events")
    void shouldReturnAllEvents() {
        when(eventRepository.findAll()).thenReturn(Flux.just(mock(Event.class), mock(Event.class)));

        StepVerifier.create(eventService.getAllEvents())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update event successfully")
    void shouldUpdateEventSuccessfully() {
        Event existing = new Event(eventId, "Old", "Old", 10, EventStatus.ACTIVE);
        Event updatedRequest = new Event(eventId, "New", "New", 20, EventStatus.ACTIVE);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(eventService.updateEvent(eventId, updatedRequest))
                .assertNext(savedEvent -> {
                    assert savedEvent.getName().equals("New");
                    assert savedEvent.getTotalCapacity() == 20;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cancel event by setting status to CANCELLED")
    void shouldDeleteEventSuccessfully() {
        Event existing = new Event(eventId, "Concert", "Bogota", 10, EventStatus.ACTIVE);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(eventService.deleteEvent(eventId))
                .verifyComplete();

        verify(eventRepository).save(argThat(event ->
                event.getStatus() == EventStatus.CANCELLED
        ));
    }
}