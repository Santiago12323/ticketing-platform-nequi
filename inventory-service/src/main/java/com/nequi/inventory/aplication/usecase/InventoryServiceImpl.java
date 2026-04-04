package com.nequi.inventory.aplication.usecase;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.SqsInventoryPublisher;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final SqsInventoryPublisher sqsInventoryPublisher;

    public Mono<Void> reserve(EventId eventId, Set<TicketId> ticketIds, OrderId orderId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException("EVENT_NOT_FOUND", "Event not found")))
                .doOnNext(Event::validateSellable)
                .flatMap(event -> ticketRepository.reserveAll(eventId, convert(ticketIds), orderId))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof BusinessException &&
                                "CONCURRENCY_ERROR".equals(((BusinessException) throwable).getErrorCode())))
                .flatMap(sqsInventoryPublisher::publishInventoryResponse)
                .onErrorResume(e -> {
                    log.error("[RESERVE_ERROR] Order: {} - {}", orderId.value(), e.getMessage());
                    return Mono.empty();
                });
    }

    private Set<String> convert(Set<TicketId> ticketIds) {
        if (ticketIds == null) return Set.of();
        return ticketIds.stream()
                .map(TicketId::value)
                .collect(Collectors.toSet());
    }

    private boolean isRetryable(Throwable e) {
        return !(e instanceof BusinessException);
    }

    @Override
    public Mono<Void> confirm(EventId eventId, Set<TicketId> tickets) {
        return eventRepository.existsById(eventId.value())
                .flatMap(exists -> {
                    if (!exists) return Mono.error(new BusinessException("EVENT_NOT_FOUND", "Evento inexistente"));

                    Set<String> ticketIds = tickets.stream()
                            .map(TicketId::value)
                            .collect(Collectors.toSet());

                    return ticketRepository.confirmAll(eventId, ticketIds);
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                        .filter(throwable -> !(throwable instanceof BusinessException))
                )
                .then();
    }

    @Override
    public Flux<Ticket> getTicketsByEvent(EventId eventId) {
        return ticketRepository.findAllByEventId(eventId);
    }

    @Override
    public Flux<Ticket> getAvailableTicketsByEvent(EventId eventId) {
        return ticketRepository.findAvailableByEventId(eventId);
    }

    @Override
    public Mono<Void> releaseReservedStock(OrderId orderId, Set<TicketId> ticketIds) {
        return Flux.fromIterable(ticketIds)
                .flatMap(ticketId -> releaseTicket(ticketId, orderId)
                                .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                                        .filter(e -> !(e instanceof BusinessException)) // solo reintenta errores técnicos
                                        .doBeforeRetry(signal -> log.warn(
                                                "[RELEASE_RETRY] Ticket: {} intento: {}",
                                                ticketId.value(), signal.totalRetries()
                                        ))
                                )
                                .onErrorResume(e -> {
                                    log.error("[RELEASE_FAILED] Ticket: {} orderId: {} - {}",
                                            ticketId.value(), orderId.value(), e.getMessage());
                                    return Mono.empty();
                                }),
                        10
                )
                .then();
    }

    private Mono<Void> releaseTicket(TicketId ticketId, OrderId orderId) {
        return ticketRepository.findById(ticketId)
                .flatMap(ticket -> {
                    if (!ticket.canBeReleasedBy(orderId)) {
                        log.warn("[RELEASE_SKIP] Ticket {} no pertenece a orden {}",
                                ticketId.value(), orderId.value());
                        return Mono.empty();
                    }
                    ticket.expire();
                    return ticketRepository.save(ticket).then();
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("[RELEASE_SKIP] Ticket {} no encontrado", ticketId.value())
                ));
    }
}