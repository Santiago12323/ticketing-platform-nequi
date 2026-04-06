package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.port.out.OrderHistoryRepository;
import com.nequi.ticketing_service.domain.port.out.OrderHistoryService;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderHistoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderHistoryServiceImpl implements OrderHistoryService {

    private final OrderHistoryRepository repository;

    @Override
    public Mono<Void> recordTimestamp(OrderId orderId, OrderStatus from, OrderStatus to, String details) {
        if (orderId == null || to == null) {
            return Mono.error(new IllegalArgumentException("OrderId y estado destino son obligatorios"));
        }
        String fromStr = (from != null) ? from.name() : "START";
        String finalDetails = (details != null && !details.isBlank()) ? details : "No additional details provided";

        return repository.saveHistory(orderId.value(), fromStr, to.name(), finalDetails)
                .doOnError(e -> log.error("Fallo al persistir historial de orden {}", orderId.value(), e));
    }

    @Override
    public Flux<OrderHistoryEntity> getHistory(OrderId orderId) {
        return repository.findByOrderId(orderId.value());
    }
}