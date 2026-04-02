package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.statemachine.TicketStatus;
import com.nequi.inventory.domain.valueobject.EventId;
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

import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoTicketRepository implements TicketRepository {

    private final DynamoDBMapper mapper;
    private final TicketEntityMapper ticketEntityMapper;
    private final TicketFactory ticketFactory;
    private final EventRepository eventRepository;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Override
    public Mono<Ticket> findById(EventId eventId, String ticketId) {
        return Mono.fromCallable(() -> mapper.load(TicketEntity.class, eventId, ticketId))
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
        return Mono.fromCallable(() -> {
            TicketEntity entity = ticketEntityMapper.toEntity(ticket);
            DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();

            if (ticket.getStatus() != TicketStatus.AVAILABLE) {
                saveExpression.withExpectedEntry(
                        "status",
                        new ExpectedAttributeValue(new AttributeValue().withS(TicketStatus.AVAILABLE.name()))
                );
            }

            mapper.save(entity, saveExpression);
            if (auditEnabled) log.info("Ticket saved successfully: {}", entity.getTicketId());
            return ticket;

        }).onErrorMap(e -> {
            if (auditEnabled) log.error("Failed to save ticket: {}. Error: {}", ticket.getTicketId().value(), e.getMessage());
            return new BusinessException("TICKET_ALREADY_RESERVED", "Ticket is no longer available or state mismatch");
        });
    }

    @Override
    public Mono<InventoryResponse> reserveAll(EventId eventId, Set<String> ticketIds, String orderId) {

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
                                                .onErrorResume(e ->
                                                        Mono.just(new Result(ticketId, false))
                                                )
                                )
                                .collectList()
                                .flatMap(results -> {

                                    List<String> failed = results.stream()
                                            .filter(r -> !r.success)
                                            .map(r -> r.ticketId)
                                            .toList();

                                    if (!failed.isEmpty()) {
                                        return rollbackReservations(eventId, ticketIds)
                                                .thenReturn(InventoryResponse.failure(orderId, failed));
                                    }

                                    return Mono.just(InventoryResponse.success(orderId));
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
                                        return ticket.cancel()
                                                .flatMap(this::save);
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
                                        return ticket.failPayment()
                                                .flatMap(this::save);
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
        return Mono.fromCallable(() -> {
            TicketEntity entity = mapper.load(TicketEntity.class, eventId, ticketId);
            boolean available = entity != null && entity.getStatus() == TicketStatus.AVAILABLE;
            if (auditEnabled) log.info("Availability check for ticket {}: {}", ticketId, available);
            return available;
        });
    }
}