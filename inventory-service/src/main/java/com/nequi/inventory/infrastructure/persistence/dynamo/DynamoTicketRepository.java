package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.TicketEntity;
import com.nequi.inventory.infrastructure.persistence.factory.TicketFactory;
import com.nequi.inventory.infrastructure.persistence.mapper.TicketEntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
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
    private final TicketFactory ticketFactory;
    private final EventRepository eventRepository;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    private DynamoDbAsyncTable<TicketEntity> table() {
        return enhancedClient.table("Ticket", TableSchema.fromBean(TicketEntity.class));
    }

    @Override
    public Mono<Ticket> findById(EventId eventId, String ticketId) {
        Key key = Key.builder()
                .partitionValue(eventId.value())
                .sortValue(ticketId)
                .build();

        return Mono.fromFuture(() -> table().getItem(key))
                .flatMap(entity -> {
                    if (entity == null) {
                        if (auditEnabled) log.warn("Ticket not found: {} for event: {}", ticketId, eventId);
                        return Mono.empty();
                    }
                    return ticketFactory.fromEntity(entity);
                });
    }

    @Override
    public Mono<Ticket> save(Ticket ticket) {
        TicketEntity entity = ticketEntityMapper.toEntity(ticket);

        PutItemEnhancedRequest<TicketEntity> request;

        if (ticket.getStatus() != TicketStatus.AVAILABLE) {
            Expression condition = Expression.builder()
                    .expression("#s = :available")
                    .expressionNames(Map.of("#s", "status"))
                    .expressionValues(Map.of(
                            ":available", AttributeValue.builder()
                                    .s(TicketStatus.AVAILABLE.name())
                                    .build()))
                    .build();

            request = PutItemEnhancedRequest.<TicketEntity>builder(TicketEntity.class)
                    .item(entity)
                    .conditionExpression(condition)
                    .build();
        } else {
            request = PutItemEnhancedRequest.<TicketEntity>builder(TicketEntity.class)
                    .item(entity)
                    .build();
        }

        return Mono.fromFuture(() -> table().putItem(request))
                .thenReturn(ticket)
                .onErrorMap(ConditionalCheckFailedException.class, e -> {
                    if (auditEnabled) log.error("Failed to save ticket: {}", ticket.getTicketId().value());
                    return new BusinessException("TICKET_ALREADY_RESERVED", "Ticket is no longer available or state mismatch");
                });
    }

    @Override
    public Mono<InventoryResponse> reserveAll(EventId eventId, Set<String> ticketIds, OrderId orderId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException("EVENT_NOT_FOUND", "Event not found")))
                .flatMap(event ->
                        Flux.fromIterable(ticketIds)
                                .concatMap(ticketId ->
                                        findById(eventId, ticketId)
                                                .switchIfEmpty(Mono.error(new BusinessException("TICKET_NOT_FOUND", "Ticket not found")))
                                                .flatMap(ticket -> ticket.reserve(event))
                                                .flatMap(this::save)
                                                .thenReturn(new Result(ticketId, true))
                                                .onErrorResume(e -> Mono.just(new Result(ticketId, false)))
                                )
                                .collectList()
                                .flatMap(results -> {
                                    List<String> failed = results.stream()
                                            .filter(r -> !r.success())
                                            .map(Result::ticketId)
                                            .toList();

                                    if (!failed.isEmpty()) {
                                        return rollbackReservations(eventId, ticketIds)
                                                .thenReturn(InventoryResponse.failure(orderId.value(), failed));
                                    }
                                    return Mono.just(InventoryResponse.success(orderId.value()));
                                })
                );
    }

    private record Result(String ticketId, boolean success) {}

    private Mono<Void> rollbackReservations(EventId eventId, Set<String> ticketIds) {
        return Flux.fromIterable(ticketIds)
                .concatMap(ticketId ->
                        findById(eventId, ticketId)
                                .flatMap(ticket -> {
                                    if (ticket.getStatus() == TicketStatus.RESERVED) {
                                        return ticket.cancel().flatMap(this::save);
                                    }
                                    return Mono.empty();
                                })
                                .onErrorResume(e -> {
                                    log.error("Rollback failed for ticket {}", ticketId, e);
                                    return Mono.empty();
                                })
                )
                .then();
    }

    @Override
    public Mono<Void> confirmAll(EventId eventId, Set<String> ticketIds) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException("EVENT_NOT_FOUND", "Event not found")))
                .flatMap(event ->
                        Flux.fromIterable(ticketIds)
                                .concatMap(ticketId ->
                                        findById(eventId, ticketId)
                                                .switchIfEmpty(Mono.error(new BusinessException("TICKET_NOT_FOUND", "Ticket not found")))
                                                .flatMap(ticket -> ticket.confirmPayment())
                                                .flatMap(this::save)
                                )
                                .collectList()
                                .then()
                                .onErrorResume(error ->
                                        rollbackPayments(eventId, ticketIds)
                                                .then(Mono.error(error))
                                )
                );
    }

    private Mono<Void> rollbackPayments(EventId eventId, Set<String> ticketIds) {
        return Flux.fromIterable(ticketIds)
                .concatMap(ticketId ->
                        findById(eventId, ticketId)
                                .flatMap(ticket -> {
                                    if (ticket.getStatus() == TicketStatus.SOLD) {
                                        return ticket.failPayment().flatMap(this::save);
                                    }
                                    return Mono.empty();
                                })
                                .onErrorResume(e -> {
                                    log.error("Rollback payment failed for ticket {}", ticketId, e);
                                    return Mono.empty();
                                })
                )
                .then();
    }

    @Override
    public Mono<Boolean> isAvailable(EventId eventId, String ticketId) {
        Key key = Key.builder()
                .partitionValue(eventId.value())
                .sortValue(ticketId)
                .build();

        return Mono.fromFuture(() -> table().getItem(key))
                .map(entity -> entity != null && TicketStatus.AVAILABLE.name().equals(entity.getStatus()))
                .defaultIfEmpty(false)
                .doOnNext(available -> {
                    if (auditEnabled) log.info("Availability check for ticket {}: {}", ticketId, available);
                });
    }

    @Override
    public Flux<Ticket> findAllByEventId(EventId eventId) {
        Key key = Key.builder()
                .partitionValue(eventId.value())
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(key))
                .build();

        return Flux.from(table().query(request).items())
                .flatMap(entity -> ticketFactory.fromEntity(entity));
    }

    @Override
    public Flux<Ticket> findAvailableByEventId(EventId eventId) {
        Key key = Key.builder()
                .partitionValue(eventId.value())
                .build();

        Expression filter = Expression.builder()
                .expression("#s = :available")
                .expressionNames(Map.of("#s", "status"))
                .expressionValues(Map.of(
                        ":available", AttributeValue.builder()
                                .s(TicketStatus.AVAILABLE.name())
                                .build()))
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(key))
                .filterExpression(filter)
                .build();

        return Flux.from(table().query(request).items())
                .flatMap(entity -> ticketFactory.fromEntity(entity));
    }
}