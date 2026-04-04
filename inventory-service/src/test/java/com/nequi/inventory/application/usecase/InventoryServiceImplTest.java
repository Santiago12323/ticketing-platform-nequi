package com.nequi.inventory.application.usecase;

import com.nequi.inventory.aplication.usecase.InventoryServiceImpl;
import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.SqsInventoryPublisher;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.RequestId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ConcurrentModificationException;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SqsInventoryPublisher sqsInventoryPublisher;

    @InjectMocks
    private InventoryServiceImpl inventoryService;


    @Test
    @DisplayName("Should reserve tickets and publish to SQS successfully")
    void shouldReserveSuccessfully() {
        // Arrange
        EventId eventId = EventId.newId();
        OrderId orderId = new OrderId(UUID.randomUUID().toString());
        Set<TicketId> tickets = Set.of(TicketId.generate(), TicketId.generate());

        Event mockEvent = mock(Event.class);
        InventoryResponse mockResponse = mock(InventoryResponse.class);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));
        doNothing().when(mockEvent).validateSellable();

        when(ticketRepository.reserveAll(any(), any(), any())).thenReturn(Mono.just(mockResponse));
        when(sqsInventoryPublisher.publishInventoryResponse(mockResponse)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = inventoryService.reserve(eventId, tickets, orderId);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(eventRepository).findById(eventId);
        verify(ticketRepository).reserveAll(eq(eventId), anySet(), eq(orderId));
        verify(sqsInventoryPublisher).publishInventoryResponse(mockResponse);
    }

    @Test
    @DisplayName("Should retry when ConcurrentModificationException occurs in DynamoDB")
    void shouldRetryOnConcurrentModificationException() {
        // Arrange
        EventId eventId = EventId.newId();
        RequestId requestId = new RequestId(UUID.randomUUID().toString());
        Event mockEvent = mock(Event.class);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));

        when(ticketRepository.reserveAll(any(), any(), any()))
                .thenReturn(Mono.error(new ConcurrentModificationException("Optimistic locking failed")))
                .thenReturn(Mono.just(mock(InventoryResponse.class)));

        when(sqsInventoryPublisher.publishInventoryResponse(any())).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = inventoryService.reserve(eventId, Set.of(TicketId.generate()), OrderId.newId());

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(ticketRepository, times(2)).reserveAll(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail when event is not found during reservation")
    void shouldFailReserveWhenEventNotFound() {
        // Arrange
        EventId eventId = EventId.newId();
        when(eventRepository.findById(eventId)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = inventoryService.reserve(eventId, Set.of(), OrderId.newId());

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_FOUND"))
                .verify();

        verify(ticketRepository, never()).reserveAll(any(), any(), any());
    }

    @Test
    @DisplayName("Should confirm tickets successfully")
    void shouldConfirmSuccessfully() {
        // Arrange
        EventId eventId = EventId.newId();
        Set<TicketId> tickets = Set.of(TicketId.generate());

        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(true));
        when(ticketRepository.confirmAll(eq(eventId), anySet())).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = inventoryService.confirm(eventId, tickets);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(ticketRepository).confirmAll(eq(eventId), anySet());
    }

    @Test
    @DisplayName("Should retry on confirmation failure and eventually fail if all retries exhausted")
    void shouldRetryOnConfirmationFailure() {
        // Arrange
        EventId eventId = EventId.newId();
        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(true));

        // Fails always
        when(ticketRepository.confirmAll(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Database Timeout")));

        // Act
        Mono<Void> result = inventoryService.confirm(eventId, Set.of(TicketId.generate()));

        // Assert
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(ticketRepository, times(4)).confirmAll(any(), any());
    }

    @Test
    @DisplayName("Should fail confirm when event does not exist")
    void shouldFailConfirmWhenEventNotFound() {
        // Arrange
        EventId eventId = EventId.newId();
        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(false));

        // Act
        Mono<Void> result = inventoryService.confirm(eventId, Set.of());

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_FOUND"))
                .verify();

        verify(ticketRepository, never()).confirmAll(any(), any());
    }

    @Test
    @DisplayName("Should fail when event validation (validateSellable) fails")
    void shouldFailWhenEventIsNotSellable() {
        // Arrange
        EventId eventId = EventId.newId();
        Event mockEvent = mock(Event.class);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));
        doThrow(new BusinessException("EVENT_NOT_ACTIVE", "Event is cancelled"))
                .when(mockEvent).validateSellable();

        // Act
        Mono<Void> result = inventoryService.reserve(eventId, Set.of(TicketId.generate()), OrderId.newId());

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_ACTIVE"))
                .verify();

        verify(ticketRepository, never()).reserveAll(any(), any(), any());
    }
}