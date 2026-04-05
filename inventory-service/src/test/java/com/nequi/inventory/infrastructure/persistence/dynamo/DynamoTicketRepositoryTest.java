package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.enums.Type;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.TicketEntity;
import com.nequi.inventory.infrastructure.persistence.mapper.TicketEntityMapper;
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
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoTicketRepositoryTest {

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;
    @Mock
    private TicketEntityMapper ticketEntityMapper;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private DynamoDbAsyncTable<TicketEntity> ticketTable;
    @Mock
    private DynamoDbAsyncIndex<TicketEntity> ticketIndex;

    @InjectMocks
    private DynamoTicketRepository dynamoTicketRepository;

    private final EventId eventId = EventId.of(UUID.randomUUID().toString());
    private final String ticketIdStr = "T-123";
    private final OrderId orderId = OrderId.of(UUID.randomUUID().toString());

    @BeforeEach
    void setUp() {
        lenient().when(enhancedClient.table(eq("Ticket"), any(TableSchema.class)))
                .thenReturn(ticketTable);
    }

    @Test
    @DisplayName("Should find ticket by PK and SK")
    void findByIdAnEventIdSuccess() {
        TicketEntity entity = new TicketEntity();
        Ticket domain = mock(Ticket.class);

        when(ticketTable.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(entity));
        when(ticketEntityMapper.toDomain(entity)).thenReturn(domain);

        StepVerifier.create(dynamoTicketRepository.findByIdAnEventId(eventId, ticketIdStr))
                .expectNext(domain)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find ticket using GSI index")
    @SuppressWarnings("unchecked")
    void findByIdViaIndex() {
        Ticket mockTicket = mock(Ticket.class);
        TicketId tId = TicketId.of(ticketIdStr);
        when(mockTicket.getTicketId()).thenReturn(tId);

        Page<TicketEntity> mockPage = mock(Page.class);
        when(mockPage.items()).thenReturn(List.of(new TicketEntity()));

        SdkPublisher<Page<TicketEntity>> publisher = SdkPublisher.adapt(Flux.just(mockPage));

        when(ticketTable.index("ticketId-index")).thenReturn(ticketIndex);
        when(ticketIndex.query(any(QueryEnhancedRequest.class))).thenReturn(publisher);
        when(ticketEntityMapper.toDomain(any())).thenReturn(mockTicket);

        StepVerifier.create(dynamoTicketRepository.findById(tId))
                .expectNext(mockTicket)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw BusinessException on ConditionalCheckFailedException")
    void saveWithCondition_Conflict() {
        Ticket mockTicket = mock(Ticket.class);
        when(mockTicket.getTicketId()).thenReturn(TicketId.of(ticketIdStr));
        when(ticketEntityMapper.toEntity(any())).thenReturn(new TicketEntity());

        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(ConditionalCheckFailedException.builder().message("Conflict").build());
        when(ticketTable.putItem(any(PutItemEnhancedRequest.class))).thenReturn(failedFuture);

        StepVerifier.create(dynamoTicketRepository.save(mockTicket))
                .expectErrorMatches(e -> e instanceof BusinessException &&
                        ((BusinessException) e).getErrorCode().equals("CONCURRENCY_ERROR"))
                .verify();
    }

    @Test
    @DisplayName("Should reserveAll successfully when all tickets are available")
    void reserveAllSuccess() {
        Event activeEvent = new Event(eventId, "Event", "Loc", 10, EventStatus.ACTIVE);
        Ticket mockTicket = mock(Ticket.class);

        when(eventRepository.findById(eventId)).thenReturn(Mono.just(activeEvent));
        when(ticketTable.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(new TicketEntity()));
        when(ticketEntityMapper.toDomain(any())).thenReturn(mockTicket);
        when(ticketTable.putItem(any(PutItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(dynamoTicketRepository.reserveAll(eventId, Set.of(ticketIdStr), orderId))
                .assertNext(res -> assertTrue(res.success()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should trigger rollback in reserveAll if one ticket fails")
    void reserveAllWithRollback() {
        Event activeEvent = new Event(eventId, "Event", "Loc", 10, EventStatus.ACTIVE);
        Ticket mockTicket = mock(Ticket.class);
        TicketEntity entity = new TicketEntity();

        when(mockTicket.getStatus()).thenReturn(TicketStatus.RESERVED);
        when(mockTicket.getOrderId()).thenReturn(orderId);
        when(eventRepository.findById(eventId)).thenReturn(Mono.just(activeEvent));

        when(ticketTable.getItem(any(Key.class)))
                .thenReturn(CompletableFuture.completedFuture(entity))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.completedFuture(entity))
                .thenReturn(CompletableFuture.completedFuture(null));

        when(ticketEntityMapper.toDomain(any())).thenReturn(mockTicket);
        when(ticketTable.putItem(any(PutItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(dynamoTicketRepository.reserveAll(eventId, Set.of(ticketIdStr, "T-ERROR"), orderId))
                .assertNext(res -> {
                    assertFalse(res.success());
                    assertEquals(Type.RESERVE, res.type());
                })
                .verifyComplete();

        verify(mockTicket, atLeastOnce()).cancel();
    }

    @Test
    @DisplayName("Should rollback payments if confirmAll fails")
    void confirmAllWithRollback() {
        Event activeEvent = new Event(eventId, "Event", "Loc", 10, EventStatus.ACTIVE);
        Ticket mockTicket = mock(Ticket.class);
        TicketEntity entity = new TicketEntity();

        when(mockTicket.getStatus()).thenReturn(TicketStatus.SOLD);
        when(eventRepository.findById(eventId)).thenReturn(Mono.just(activeEvent));

        when(ticketTable.getItem(any(Key.class)))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.completedFuture(entity));

        when(ticketEntityMapper.toDomain(entity)).thenReturn(mockTicket);
        when(ticketTable.putItem(any(PutItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(dynamoTicketRepository.confirmAll(eventId, Set.of(ticketIdStr), orderId))
                .assertNext(res -> {
                    assertFalse(res.success());
                    assertEquals(Type.PAYMENT, res.type());
                })
                .verifyComplete();

        verify(mockTicket).failPayment();
    }

    @Test
    @DisplayName("Should return false in isAvailable if ticket is null")
    void isAvailableNull() {
        when(ticketTable.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(dynamoTicketRepository.isAvailable(eventId, ticketIdStr))
                .expectNext(false)
                .verifyComplete();
    }
}