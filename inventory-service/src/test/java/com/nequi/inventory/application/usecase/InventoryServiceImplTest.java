package com.nequi.inventory.application.usecase;

import com.nequi.inventory.aplication.usecase.InventoryServiceImpl;
import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.SqsInventoryPublisher;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import com.nequi.inventory.infrastructure.messaging.sqs.enums.Type;
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

import java.util.List;
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
    private Set<TicketId> ticketIdSet;
    private List<TicketId> ticketIdList;

    @BeforeEach
    void setUp() {
        eventId = EventId.newId();
        orderId = new OrderId(UUID.randomUUID().toString());
        ticketIdSet = Set.of(TicketId.generate(), TicketId.generate());
        ticketIdList = List.copyOf(ticketIdSet);
    }

    @Test
    @DisplayName("Should reserve tickets and publish RESERVE response successfully")
    void shouldReserveSuccessfully() {
        // Arrange
        Event mockEvent = mock(Event.class);
        InventoryResponse mockResponse = InventoryResponse.success(orderId.value(), Type.RESERVE);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));
        doNothing().when(mockEvent).validateSellable();

        when(ticketRepository.reserveAll(eq(eventId), anySet(), eq(orderId))).thenReturn(Mono.just(mockResponse));
        when(sqsInventoryPublisher.publishInventoryResponse(any())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.reserve(eventId, ticketIdSet, orderId))
                .verifyComplete();

        verify(eventRepository).findById(eventId);
        verify(ticketRepository).reserveAll(eq(eventId), anySet(), eq(orderId));
        verify(sqsInventoryPublisher).publishInventoryResponse(argThat(res -> res.type() == Type.RESERVE));
    }

    @Test
    @DisplayName("Should retry reservation when CONCURRENCY_ERROR occurs")
    void shouldRetryOnConcurrencyError() {
        // Arrange
        Event mockEvent = mock(Event.class);
        BusinessException concurrencyError = new BusinessException("CONCURRENCY_ERROR", "Lock conflict");
        InventoryResponse mockResponse = InventoryResponse.success(orderId.value(), Type.RESERVE);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));

        when(ticketRepository.reserveAll(any(), any(), any()))
                .thenReturn(Mono.error(concurrencyError))
                .thenReturn(Mono.just(mockResponse));

        when(sqsInventoryPublisher.publishInventoryResponse(any())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.reserve(eventId, ticketIdSet, orderId))
                .verifyComplete();

        // Verificamos
        verify(ticketRepository, times(2)).reserveAll(any(), any(), any());
    }

    @Test
    @DisplayName("Should return all tickets for an event")
    void shouldGetTicketsByEvent() {
        // Arrange
        Ticket mockTicket = mock(Ticket.class);
        when(ticketRepository.findAllByEventId(eventId)).thenReturn(Flux.just(mockTicket));

        // Act & Assert
        StepVerifier.create(inventoryService.getTicketsByEvent(eventId))
                .expectNext(mockTicket)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return available tickets for an event")
    void shouldGetAvailableTicketsByEvent() {
        // Arrange
        Ticket mockTicket = mock(Ticket.class);
        when(ticketRepository.findAvailableByEventId(eventId)).thenReturn(Flux.just(mockTicket));

        // Act & Assert
        StepVerifier.create(inventoryService.getAvailableTicketsByEvent(eventId))
                .expectNext(mockTicket)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should release reserved stock successfully")
    void shouldReleaseReservedStockSuccessfully() {
        // Arrange
        TicketId t1 = ticketIdSet.iterator().next();
        Ticket mockTicket = mock(Ticket.class);

        when(ticketRepository.findById(any(TicketId.class))).thenReturn(Mono.just(mockTicket));
        when(mockTicket.canBeReleasedBy(orderId)).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(Mono.just(mockTicket));
        when(mockTicket.getTicketId()).thenReturn(t1);

        // Act & Assert
        StepVerifier.create(inventoryService.releaseReservedStock(orderId, Set.of(t1)))
                .verifyComplete();

        verify(mockTicket).expire();
        verify(ticketRepository).save(mockTicket);
    }

    @Test
    @DisplayName("Should skip release when ticket not found (switchIfEmpty)")
    void shouldSkipReleaseWhenTicketNotFound() {
        // Arrange
        TicketId t1 = TicketId.generate();
        when(ticketRepository.findById(t1)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.releaseReservedStock(orderId, Set.of(t1)))
                .verifyComplete();

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should skip release when ticket cannot be released by order (Business Logic)")
    void shouldSkipReleaseWhenInvalidOrder() {
        // Arrange
        TicketId t1 = TicketId.generate();
        Ticket mockTicket = mock(Ticket.class);

        when(ticketRepository.findById(t1)).thenReturn(Mono.just(mockTicket));
        when(mockTicket.canBeReleasedBy(orderId)).thenReturn(false);

        // Act & Assert
        StepVerifier.create(inventoryService.releaseReservedStock(orderId, Set.of(t1)))
                .verifyComplete();

        verify(mockTicket, never()).expire();
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retry on DB error and then succeed")
    void shouldRetryOnDbError() {
        // Arrange
        TicketId t1 = TicketId.generate();
        Ticket mockTicket = mock(Ticket.class);

        when(ticketRepository.findById(t1)).thenReturn(Mono.just(mockTicket));
        when(mockTicket.canBeReleasedBy(orderId)).thenReturn(true);
        when(mockTicket.getTicketId()).thenReturn(t1);

        when(ticketRepository.save(any()))
                .thenReturn(Mono.error(new RuntimeException("DB Connection Fail")))
                .thenReturn(Mono.just(mockTicket));

        // Act & Assert
        StepVerifier.create(inventoryService.releaseReservedStock(orderId, Set.of(t1)))
                .verifyComplete();

        verify(ticketRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Should handle and log error when all retries fail (onErrorResume)")
    void shouldHandleErrorWhenRetriesExhausted() {
        TicketId t1 = TicketId.generate();
        Ticket mockTicket = mock(Ticket.class);

        when(ticketRepository.findById(t1)).thenReturn(Mono.just(mockTicket));
        when(mockTicket.canBeReleasedBy(orderId)).thenReturn(true);

        when(ticketRepository.save(any())).thenReturn(Mono.error(new RuntimeException("Fatal DB Error")));

        StepVerifier.create(inventoryService.releaseReservedStock(orderId, Set.of(t1)))
                .verifyComplete();

        verify(ticketRepository, times(4)).save(any());
    }

    @Test
    @DisplayName("Should handle EVENT_NOT_FOUND during reservation")
    void shouldHandleEventNotFoundInReserve() {
        // Arrange
        when(eventRepository.findById(eventId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.reserve(eventId, ticketIdSet, orderId))
                .verifyComplete();

        verify(ticketRepository, never()).reserveAll(any(), any(), any());
        verify(sqsInventoryPublisher, never()).publishInventoryResponse(any());
    }

    @Test
    @DisplayName("Should confirm tickets and publish CONFIRM response successfully")
    void shouldConfirmSuccessfully() {
        // Arrange
        InventoryResponse mockResponse = InventoryResponse.success(orderId.value(), Type.PAYMENT);

        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(true));
        when(ticketRepository.confirmAll(eq(eventId), anySet(), eq(orderId))).thenReturn(Mono.just(mockResponse));
        when(sqsInventoryPublisher.publishInventoryResponse(any())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(inventoryService.confirm(eventId, ticketIdList, orderId))
                .verifyComplete();

        verify(ticketRepository).confirmAll(eq(eventId), anySet(), eq(orderId));
        verify(sqsInventoryPublisher).publishInventoryResponse(argThat(res -> res.type() == Type.PAYMENT));
    }

    @Test
    @DisplayName("Should fail confirmation when event does not exist")
    void shouldFailConfirmWhenEventNotFound() {
        // Arrange
        when(eventRepository.existsById(eventId.value())).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(inventoryService.confirm(eventId, ticketIdList, orderId))
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("EVENT_NOT_FOUND"))
                .verify();

        verify(ticketRepository, never()).confirmAll(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail reservation when validation (validateSellable) fails")
    void shouldFailWhenEventNotSellable() {
        // Arrange
        Event mockEvent = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Mono.just(mockEvent));

        doThrow(new BusinessException("EVENT_NOT_ACTIVE", "Cannot sell"))
                .when(mockEvent).validateSellable();

        // Act & Assert
        StepVerifier.create(inventoryService.reserve(eventId, ticketIdSet, orderId))
                .verifyComplete();

        verify(ticketRepository, never()).reserveAll(any(), any(), any());
    }
}