package com.nequi.inventory.application.usecase;

import com.nequi.inventory.aplication.usecase.TicketQueryServiceImpl;
import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketQueryServiceImpl ticketQueryService;

    private final String VALID_EVENT_ID = UUID.randomUUID().toString();
    private final String VALID_TICKET_ID = UUID.randomUUID().toString();


    @Test
    @DisplayName("Should return ticket when it exists in repository")
    void shouldReturnTicketSuccessfully() {
        // Arrange
        Ticket mockTicket = mock(Ticket.class);
        when(ticketRepository.findById(any(EventId.class), eq(VALID_TICKET_ID)))
                .thenReturn(Mono.just(mockTicket));

        // Act
        Mono<Ticket> result = ticketQueryService.getTicket(VALID_EVENT_ID, VALID_TICKET_ID);

        // Assert
        StepVerifier.create(result)
                .expectNext(mockTicket)
                .verifyComplete();

        verify(ticketRepository).findById(argThat(id -> id.value().equals(VALID_EVENT_ID)), eq(VALID_TICKET_ID));
    }

    @Test
    @DisplayName("Should throw BusinessException when ticket is not found")
    void shouldFailGetTicketWhenNotFound() {
        // Arrange
        when(ticketRepository.findById(any(EventId.class), anyString()))
                .thenReturn(Mono.empty());

        // Act
        Mono<Ticket> result = ticketQueryService.getTicket(VALID_EVENT_ID, VALID_TICKET_ID);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("TICKET_NOT_FOUND"))
                .verify();
    }

    @Test
    @DisplayName("Should return correct status name when ticket exists")
    void shouldReturnTicketStatusSuccessfully() {
        Ticket mockTicket = mock(Ticket.class);
        when(mockTicket.getStatus()).thenReturn(TicketStatus.SOLD);

        when(ticketRepository.findById(any(EventId.class), eq(VALID_TICKET_ID)))
                .thenReturn(Mono.just(mockTicket));

        // Act
        Mono<String> result = ticketQueryService.getTicketStatus(VALID_EVENT_ID, VALID_TICKET_ID);

        // Assert
        StepVerifier.create(result)
                .expectNext("SOLD")
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw BusinessException when requesting status of non-existent ticket")
    void shouldFailGetStatusWhenNotFound() {
        // Arrange
        when(ticketRepository.findById(any(EventId.class), anyString()))
                .thenReturn(Mono.empty());

        // Act
        Mono<String> result = ticketQueryService.getTicketStatus(VALID_EVENT_ID, VALID_TICKET_ID);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof BusinessException &&
                        ((BusinessException) t).getErrorCode().equals("TICKET_NOT_FOUND"))
                .verify();
    }

    @Test
    @DisplayName("Should propagate infrastructure error if repository fails")
    void shouldPropagateInfrastructureError() {
        // Arrange
        when(ticketRepository.findById(any(EventId.class), anyString()))
                .thenReturn(Mono.error(new RuntimeException("DynamoDB Connection Timeout")));

        // Act
        Mono<Ticket> result = ticketQueryService.getTicket(VALID_EVENT_ID, VALID_TICKET_ID);

        // Assert
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }
}