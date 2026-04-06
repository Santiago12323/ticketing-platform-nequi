package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.exception.BusinessException;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.in.ProcessInventoryResponseUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderHistoryService;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInventoryResponseUseCaseImpl implements ProcessInventoryResponseUseCase {

    private final OrderRepository repository;
    private final OrderHistoryService historyService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CacheKeyGenerator keyGenerator;

    @Override
    public Mono<Void> execute(OrderId orderId, OrderEvent event) {
        return repository.findById(orderId)
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "Order not found: " + orderId.value())))
                .flatMap(order -> {
                    OrderStatus previousStatus = order.getStatus();

                    applyEvent(order, event);

                    return repository.updateStatus(order)
                            .flatMap(updatedOrder ->
                                    historyService.recordTimestamp(
                                                    orderId,
                                                    previousStatus,
                                                    updatedOrder.getStatus(),
                                                    "Transition triggered by event: " + event.name()
                                            )
                                            .then(Mono.defer(() -> {
                                                String key = keyGenerator.generateOrderKey(orderId.value());
                                                return redisTemplate.opsForValue().delete(key);
                                            }))
                            );
                })
                .doOnSuccess(v -> log.info("[ORDER_EVENT_PROCESSED] Order {} transitioned with event: {}", orderId.value(), event))
                .doOnError(e -> log.error("[ORDER_PROCESS_FAILED] Error processing order {}: {}", orderId.value(), e.getMessage()))
                .then();
    }

    private void applyEvent(Order order, OrderEvent event) {
        switch (event) {
            case VALIDATION_SUCCESS -> order.confirmInventory();
            case VALIDATION_FAILED  -> order.failInventory();
            case CONFIRM_PAYMENT    -> order.confirmPayment();
            case FAIL_PAYMENT       -> order.failPayment();
            case EXPIRE             -> order.expire();
            case CANCEL             -> order.cancel();
            case START_PAYMENT      -> order.startPayment();
            default -> throw new BusinessException("UNSUPPORTED_EVENT", "Event " + event + " not supported in this flow");
        }
    }
}