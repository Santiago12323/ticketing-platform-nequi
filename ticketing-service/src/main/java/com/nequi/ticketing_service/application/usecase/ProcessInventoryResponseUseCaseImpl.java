package com.nequi.ticketing_service.application.usecase;

import com.nequi.ticketing_service.domain.port.in.ProcessInventoryResponseUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans.CacheKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ProcessInventoryResponseUseCaseImpl implements ProcessInventoryResponseUseCase {

    private final OrderRepository repository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CacheKeyGenerator keyGenerator;

    @Override
    public Mono<Void> execute(OrderId orderId, boolean isSuccess) {
        return repository.findById(orderId)
                .flatMap(order -> isSuccess ? order.confirmInventory() : order.failInventory())
                .flatMap(repository::updateStatus)
                .flatMap(updatedOrder -> {
                    String key = keyGenerator.generateOrderKey(orderId.value());

                    return redisTemplate.opsForValue().delete(key)
                            .thenReturn(updatedOrder);
                })
                .then();
    }
}