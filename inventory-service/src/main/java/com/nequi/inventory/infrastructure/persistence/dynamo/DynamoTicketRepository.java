package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.TicketEntity;
import com.nequi.inventory.infrastructure.persistence.mapper.TicketEntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoTicketRepository implements TicketRepository {

    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final TicketEntityMapper ticketEntityMapper;
    private final EventRepository eventRepository;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    private DynamoDbAsyncTable<TicketEntity> table() {
        return enhancedClient.table("Ticket", TableSchema.fromBean(TicketEntity.class));
    }

    @Override
    public Mono<Ticket> findByIdAnEventId(EventId eventId, String ticketId) {
        Key key = Key.builder()
                .partitionValue(eventId.value())
                .sortValue(ticketId)
                .build();

        return Mono.fromFuture(() -> table().getItem(key))
                .map(entity -> entity == null ? null : ticketEntityMapper.toDomain(entity));
    }

    @Override
    public Mono<Ticket> findById(TicketId ticketId) {
        DynamoDbAsyncIndex<TicketEntity> ticketIndex = table().index("ticketId-index");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(ticketId.value()).build()
        );

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(1)
                .build();

        return Flux.from(ticketIndex.query(queryRequest))
                .flatMapIterable(page -> page.items())
                .next()
                .map(ticketEntityMapper::toDomain)
                .doOnNext(t -> log.info("[DB] Ticket encontrado: {}", t.getTicketId().value()))
                .switchIfEmpty(Mono.error(new BusinessException("TICKET_NOT_FOUND", "Ticket no existe en el índice")));
    }

    @Override
    public Mono<Ticket> save(Ticket ticket) {
        return saveWithCondition(ticket, null);
    }

    private Mono<Ticket> saveWithCondition(Ticket ticket, TicketStatus expectedStatus) {
        TicketEntity entity = ticketEntityMapper.toEntity(ticket);
        PutItemEnhancedRequest.Builder<TicketEntity> builder = PutItemEnhancedRequest.builder(TicketEntity.class)
                .item(entity);

        if (expectedStatus != null) {
            Expression condition = Expression.builder()
                    .expression("#s = :expected")
                    .expressionNames(Map.of("#s", "status"))
                    .expressionValues(Map.of(":expected", AttributeValue.builder().s(expectedStatus.name()).build()))
                    .build();
            builder.conditionExpression(condition);
        }

        return Mono.fromFuture(() -> table().putItem(builder.build()))
                .thenReturn(ticket)
                .onErrorMap(ConditionalCheckFailedException.class, e -> {
                    log.error("Conflict saving ticket {}: expected state {}", ticket.getTicketId().value(), expectedStatus);
                    return new BusinessException("CONCURRENCY_ERROR", "Ticket state changed or invalid transition");
                });
    }

    @Override
    public Mono<InventoryResponse> reserveAll(EventId eventId, Set<String> ticketIds, OrderId orderId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException("EVENT_NOT_FOUND", "Event not found")))
                .flatMap(this::ensureEventIsActive)
                .flatMap(event -> Flux.fromIterable(ticketIds)
                        .concatMap(id -> reserveSingleTicket(event, id))
                        .collectList())
                .flatMap(results -> handleBatchResults(orderId, eventId, ticketIds, results, true));
    }

    private Mono<Event> ensureEventIsActive(Event event) {
        if (event.getStatus() != EventStatus.ACTIVE) {
            return Mono.error(new BusinessException("EVENT_NOT_ACTIVE", "Event is not active"));
        }
        return Mono.just(event);
    }

    private Mono<Result> reserveSingleTicket(Event event, String ticketId) {
        return findByIdAnEventId(event.getEventId(), ticketId)
                .switchIfEmpty(Mono.error(new BusinessException("TICKET_NOT_FOUND", "Ticket not found")))
                .flatMap(ticket -> {
                    ticket.reserve(event);
                    return saveWithCondition(ticket, TicketStatus.AVAILABLE);
                })
                .map(t -> new Result(ticketId, true))
                .onErrorResume(e -> {
                    log.error("Failed to reserve {}: {}", ticketId, e.getMessage());
                    return Mono.just(new Result(ticketId, false));
                });
    }

    @Override
    public Mono<Void> confirmAll(EventId eventId, Set<String> ticketIds) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException("EVENT_NOT_FOUND", "Event not found")))
                .flatMap(event -> Flux.fromIterable(ticketIds)
                        .concatMap(id -> confirmSingleTicket(eventId, id))
                        .collectList())
                .flatMap(results -> handleBatchResults(null, eventId, ticketIds, results, false))
                .then();
    }

    private Mono<Result> confirmSingleTicket(EventId eventId, String ticketId) {
        return findByIdAnEventId(eventId, ticketId)
                .switchIfEmpty(Mono.error(new BusinessException("TICKET_NOT_FOUND", "Ticket not found")))
                .flatMap(ticket -> {
                    ticket.confirmPayment();
                    return saveWithCondition(ticket, TicketStatus.RESERVED);
                })
                .map(t -> new Result(ticketId, true))
                .onErrorResume(e -> {
                    log.error("Failed to confirm {}: {}", ticketId, e.getMessage());
                    return Mono.just(new Result(ticketId, false));
                });
    }

    private Mono<InventoryResponse> handleBatchResults(OrderId orderId, EventId eventId, Set<String> ticketIds, List<Result> results, boolean isReservation) {
        List<String> failedIds = results.stream()
                .filter(r -> !r.success())
                .map(Result::ticketId)
                .toList();

        String responseOrderId = orderId != null ? orderId.value() : "N/A";

        if (failedIds.isEmpty()) {
            return Mono.just(InventoryResponse.success(responseOrderId));
        }

        return (isReservation ? rollbackReservations(eventId, ticketIds) : rollbackPayments(eventId, ticketIds))
                .thenReturn(InventoryResponse.failure(responseOrderId, failedIds));
    }

    private Mono<Void> rollbackReservations(EventId eventId, Set<String> ticketIds) {
        return Flux.fromIterable(ticketIds)
                .concatMap(id -> findByIdAnEventId(eventId, id)
                        .flatMap(t -> {
                            if (t != null && t.getStatus() == TicketStatus.RESERVED) {
                                t.cancel();
                                return saveWithCondition(t, TicketStatus.RESERVED);
                            }
                            return Mono.empty();
                        })
                        .onErrorResume(e -> Mono.empty()))
                .then();
    }

    private Mono<Void> rollbackPayments(EventId eventId, Set<String> ticketIds) {
        return Flux.fromIterable(ticketIds)
                .concatMap(id -> findByIdAnEventId(eventId, id)
                        .flatMap(t -> {
                            if (t != null && t.getStatus() == TicketStatus.SOLD) {
                                t.failPayment();
                                return saveWithCondition(t, TicketStatus.SOLD);
                            }
                            return Mono.empty();
                        })
                        .onErrorResume(e -> Mono.empty()))
                .then();
    }

    @Override
    public Mono<Boolean> isAvailable(EventId eventId, String ticketId) {
        Key key = Key.builder().partitionValue(eventId.value()).sortValue(ticketId).build();
        return Mono.fromFuture(() -> table().getItem(key))
                .map(entity -> entity != null && TicketStatus.AVAILABLE.name().equals(entity.getStatus().name()))
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<Ticket> findAllByEventId(EventId eventId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(eventId.value()).build()))
                .build();
        return Flux.from(table().query(request).items())
                .map(ticketEntityMapper::toDomain);
    }

    @Override
    public Flux<Ticket> findAvailableByEventId(EventId eventId) {
        Expression filter = Expression.builder()
                .expression("#s = :av")
                .expressionNames(Map.of("#s", "status"))
                .expressionValues(Map.of(":av", AttributeValue.builder().s(TicketStatus.AVAILABLE.name()).build()))
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(eventId.value()).build()))
                .filterExpression(filter)
                .build();

        return Flux.from(table().query(request).items())
                .map(ticketEntityMapper::toDomain);
    }

    private record Result(String ticketId, boolean success) {}
}