package com.nequi.inventory.aplication.usecase;

import com.nequi.inventory.domain.exception.BusinessException;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.port.out.SqsInventoryPublisher;
import com.nequi.inventory.domain.port.out.TicketRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final SqsInventoryPublisher sqsInventoryPublisher;

    @Override
    public Mono<Void> reserve(EventId eventId, Set<TicketId> tickets, OrderId orderId) {

        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException("EVENT_NOT_FOUND", "El evento no existe")))
                .flatMap(event -> {
                    event.validateSellable();

                    Set<String> ticketIds = tickets.stream()
                            .map(TicketId::value)
                            .collect(Collectors.toSet());

                    return ticketRepository.reserveAll(eventId, ticketIds, orderId);
                })
                .flatMap(sqsInventoryPublisher::publishInventoryResponse)
                .retryWhen(
                        Retry.backoff(3, Duration.ofMillis(200))
                                .filter(this::isRetryable)
                );
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
}