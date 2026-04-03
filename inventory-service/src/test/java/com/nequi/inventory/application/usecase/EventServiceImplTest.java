package com.nequi.inventory.application.usecase;

import com.nequi.inventory.aplication.usecase.EventServiceImpl;
import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.statemachine.TicketEvent;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.statemachine.machine.TicketStateMachineFactory;
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
import org.springframework.statemachine.StateMachine;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketStateMachineFactory ticketStateMachineFactory;

    @Mock
    private StateMachine<TicketStatus, TicketEvent> stateMachine;

    @InjectMocks
    private EventServiceImpl eventService;

    private final String VALID_UUID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().when(stateMachine.startReactively()).thenReturn(Mono.empty());
    }


    @Test
    @DisplayName("Should create event and its tickets successfully")
    void shouldCreateEventSuccessfully() {
        // Arrange
        EventId eventId = EventId.newId();
        TicketId ticketId = TicketId.generate();
        Ticket realTicket = new Ticket(ticketId, eventId.value(), stateMachine);

        when(eventRepository.existsById(anyString())).thenReturn(Mono.just(false));
        when(eventRepository.save(any(Event.class))).thenReturn(Mono.just(mock(Event.class)));
        when(ticketStateMachineFactory.create(anyString())).thenReturn(Mono.just(stateMachine));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(Mono.just(realTicket));

        // Act
        Mono<Void> result = eventService.createEvent(eventId, 3, "Concert", "Bogota");

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(eventRepository).save(any());
        verify(ticketRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("Should trigger BusinessException when validateEventDoesNotExist finds an existing ID")
    void shouldTriggerExceptionWhenEventIdIsTaken() {
        // Arrange
        EventId eventId = EventId.newId();
        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(true));

        // Act
        Mono<Void> result = eventService.createEvent(eventId, 10, "Festival", "Medellín");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof BusinessException &&
                        ((BusinessException) throwable).getErrorCode().equals("EVENT_ALREADY_EXISTS") &&
                        throwable.getMessage().contains(eventId.value()))
                .verify();

        verify(eventRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should create event but zero tickets when capacity is 0")
    void shouldCreateEventWithZeroTickets() {
        // Arrange
        EventId eventId = EventId.newId();
        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(false));
        when(eventRepository.save(any(Event.class))).thenReturn(Mono.just(mock(Event.class)));

        // Act
        Mono<Void> result = eventService.createEvent(eventId, 0, "Empty Event", "Virtual");

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(eventRepository).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when event ID already exists")
    void shouldFailWhenEventAlreadyExists() {
        // Arrange
        EventId eventId = EventId.newId();
        when(eventRepository.existsById(anyString())).thenReturn(Mono.just(true));

        // Act
        Mono<Void> result = eventService.createEvent(eventId, 5, "Concert", "Bogota");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof BusinessException &&
                        ((BusinessException) throwable).getErrorCode().equals("EVENT_ALREADY_EXISTS"))
                .verify();

        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when event repository throws an infrastructure error")
    void shouldFailWhenEventRepositoryFails() {
        // Arrange
        EventId eventId = EventId.newId();
        when(eventRepository.existsById(anyString())).thenReturn(Mono.just(false));
        when(eventRepository.save(any())).thenReturn(Mono.error(new RuntimeException("Database Error")));

        // Act
        Mono<Void> result = eventService.createEvent(eventId, 2, "Test", "Loc");

        // Assert
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }


    @Test
    @DisplayName("Should return event when it exists")
    void shouldReturnEventWhenFound() {
        // Arrange
        EventId eventId = new EventId(VALID_UUID);
        Event mockEvent = new Event(VALID_UUID, "Name", "Loc", 10, EventStatus.ACTIVE);
        when(eventRepository.findById(any(EventId.class))).thenReturn(Mono.just(mockEvent));

        // Act
        Mono<Event> result = eventService.getEvent(eventId);

        // Assert
        StepVerifier.create(result)
                .expectNext(mockEvent)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw BusinessException when event is not found")
    void shouldFailWhenEventNotFound() {
        // Arrange
        EventId eventId = new EventId(VALID_UUID);
        when(eventRepository.findById(any(EventId.class))).thenReturn(Mono.empty());

        // Act
        Mono<Event> result = eventService.getEvent(eventId);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_FOUND"))
                .verify();
    }

    @Test
    @DisplayName("Should return all events")
    void shouldReturnAllEvents() {
        // Arrange
        when(eventRepository.findAll()).thenReturn(Flux.just(mock(Event.class), mock(Event.class)));

        // Act
        Flux<Event> result = eventService.getAllEvents();

        // Assert
        StepVerifier.create(result)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update event successfully")
    void shouldUpdateEventSuccessfully() {
        // Arrange
        EventId eventId = new EventId(VALID_UUID);
        Event existing = new Event(VALID_UUID, "Old", "Old", 10, EventStatus.ACTIVE);
        Event updatedRequest = new Event(VALID_UUID, "New", "New", 20, EventStatus.ACTIVE);

        when(eventRepository.findById(any(EventId.class))).thenReturn(Mono.just(existing));
        when(eventRepository.save(any())).thenReturn(Mono.just(updatedRequest));

        // Act
        Mono<Event> result = eventService.updateEvent(eventId, updatedRequest);

        // Assert
        StepVerifier.create(result)
                .expectNext(updatedRequest)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cancel event by setting status to CANCELLED")
    void shouldDeleteEventSuccessfully() {
        // Arrange
        EventId eventId = new EventId(VALID_UUID);
        Event existing = new Event(VALID_UUID, "Concert", "Bogota", 10, EventStatus.ACTIVE);

        when(eventRepository.findById(any(EventId.class))).thenReturn(Mono.just(existing));
        when(eventRepository.save(any())).thenReturn(Mono.just(existing));

        // Act
        Mono<Void> result = eventService.deleteEvent(eventId);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(eventRepository).save(argThat(event ->
                event.getStatus() == com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus.CANCELLED
        ));
    }

    @Test
    @DisplayName("Should fail when deleting a non-existent event")
    void shouldFailDeletingNonExistentEvent() {
        // Arrange
        EventId eventId = new EventId(VALID_UUID);
        when(eventRepository.findById(any(EventId.class))).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = eventService.deleteEvent(eventId);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_FOUND"))
                .verify();
    }
}