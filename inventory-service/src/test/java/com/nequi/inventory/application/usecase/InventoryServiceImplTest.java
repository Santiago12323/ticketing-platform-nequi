package com.nequi.inventory.application.usecase;

import com.nequi.inventory.aplication.usecase.InventoryServiceImpl;
import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.SqsInventoryPublisher;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

    private EventId eventId;
    private OrderId orderId;
    private Set<TicketId> tickets;

    @BeforeEach
    void setUp() {
        eventId = EventId.newId();
        orderId = new OrderId(UUID.randomUUID().toString());
        tickets = Set.of(TicketId.generate(), TicketId.generate());
    }

    @Test
    @DisplayName("Should reserve tickets and publish to SQS successfully")
    void shouldReserveSuccessfully() {
        // Arrange
        Event mockEvent = mock(Event.class);
        InventoryResponse mockResponse = InventoryResponse.success(orderId.value());

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));
        doNothing().when(mockEvent).validateSellable();

        when(ticketRepository.reserveAll(any(), any(), any())).thenReturn(Mono.just(mockResponse));
        when(sqsInventoryPublisher.publishInventoryResponse(any())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.reserve(eventId, tickets, orderId))
                .verifyComplete();

        verify(eventRepository).findById(eventId);
        verify(ticketRepository).reserveAll(eq(eventId), anySet(), eq(orderId));
        verify(sqsInventoryPublisher).publishInventoryResponse(mockResponse);
    }

    @Test
    @DisplayName("Should retry when CONCURRENCY_ERROR occurs in DynamoDB")
    void shouldRetryOnConcurrentModificationException() {
        // Arrange
        Event mockEvent = mock(Event.class);
        BusinessException concurrencyError = new BusinessException("CONCURRENCY_ERROR", "Optimistic locking failed");
        InventoryResponse mockResponse = InventoryResponse.success(orderId.value());

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));

        when(ticketRepository.reserveAll(any(), any(), any()))
                .thenReturn(Mono.error(concurrencyError))
                .thenReturn(Mono.just(mockResponse));

        when(sqsInventoryPublisher.publishInventoryResponse(any())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.reserve(eventId, tickets, orderId))
                .verifyComplete();

        verify(ticketRepository, times(2)).reserveAll(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail when event is not found during reservation")
    void shouldFailReserveWhenEventNotFound() {
        // Arrange:
        when(eventRepository.findById(eventId)).thenReturn(Mono.empty());

        // Act & Assert:
        StepVerifier.create(inventoryService.reserve(eventId, tickets, orderId))
                .verifyComplete();

        verify(ticketRepository, never()).reserveAll(any(), any(), any());

        // Verificamos que se intentó buscar el evento
        verify(eventRepository).findById(eventId);
    }

    @Test
    @DisplayName("Should confirm tickets successfully")
    void shouldConfirmSuccessfully() {
        // Arrange
        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(true));
        when(ticketRepository.confirmAll(eq(eventId), anySet())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.confirm(eventId, tickets))
                .verifyComplete();

        verify(ticketRepository).confirmAll(eq(eventId), anySet());
    }

    @Test
    @DisplayName("Should fail confirm when event does not exist")
    void shouldFailConfirmWhenEventNotFound() {
        // Arrange
        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(inventoryService.confirm(eventId, tickets))
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_FOUND"))
                .verify();

        verify(ticketRepository, never()).confirmAll(any(), any());
    }

    @Test
    @DisplayName("Should fail when event validation (validateSellable) fails")
    void shouldFailWhenEventIsNotSellable() {
        // Arrange
        Event mockEvent = mock(Event.class);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));

        doThrow(new BusinessException("EVENT_NOT_ACTIVE", "Event is cancelled"))
                .when(mockEvent).validateSellable();

        // Act & Assert
        StepVerifier.create(inventoryService.reserve(eventId, tickets, orderId))
                .verifyComplete();

        verify(ticketRepository, never()).reserveAll(any(), any(), any());
    }
}