package com.nequi.inventory.aplication.usecase;

import com.nequi.inventory.domain.model.event.Event;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.port.out.EventRepository;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.RequestId;
import com.nequi.inventory.domain.valueobject.SeatId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;

@Service
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private EventRepository repository;

    @Override
    public Mono<Void> reserve(EventId eventId, Set<SeatId> seats, RequestId requestId) {
        return repository.isDuplicateRequest(requestId.value())
                .flatMap(duplicate -> {
                    if (duplicate) return Mono.empty();
                    return repository.findById(eventId)
                            .flatMap(event -> {
                                event.reserveSeats(seats);
                                event.incrementVersion();
                                return repository.save(event)
                                        .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                                        .then(repository.markRequestProcessed(requestId.value()));
                            });
                });
    }

    @Override
    public Mono<Void> confirm(EventId eventId, Set<SeatId> seats, RequestId requestId) {
        return repository.isDuplicateRequest(requestId.value())
                .flatMap(duplicate -> {
                    if (duplicate) return Mono.empty();
                    return repository.findById(eventId)
                            .flatMap(event -> {
                                event.confirmSale(seats);
                                event.incrementVersion();
                                return repository.save(event)
                                        .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                                        .then(repository.markRequestProcessed(requestId.value()));
                            });
                });
    }

    @Override
    public Mono<Void> release(EventId eventId, Set<SeatId> seats, RequestId requestId) {
        return repository.findById(eventId)
                .flatMap(event -> {
                    event.releaseSeats(seats);
                    event.incrementVersion();
                    return repository.save(event)
                            .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                            .then(repository.markRequestProcessed(requestId.value()));
                });
    }

    @Override
    public Mono<Integer> getAvailability(EventId eventId) {
        return repository.findById(eventId)
                .map(Event::getAvailableCount);
    }
}
