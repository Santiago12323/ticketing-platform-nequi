package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.port.in.ProcessInventoryResponseUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInventoryResponseUseCaseImpl implements ProcessInventoryResponseUseCase {

    private final OrderRepository repository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CacheKeyGenerator keyGenerator;

    @Override
    public Mono<Void> execute(OrderId orderId, boolean isSuccess) {
        return repository.findById(orderId)
                .map(order -> {
                    if (isSuccess) {
                        order.confirmInventory();
                    } else {
                        order.failInventory();
                    }
                    return order;
                })
                .flatMap(repository::updateStatus)
                .flatMap(updatedOrder -> {
                    String key = keyGenerator.generateOrderKey(orderId.value());
                    return redisTemplate.opsForValue().delete(key);
                })
                .doOnSuccess(v -> log.info("[INVENTORY_PROCESSED] Order {} processed with success: {}", orderId.value(), isSuccess))
                .then();
    }
}