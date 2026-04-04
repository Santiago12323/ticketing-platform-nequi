package com.nequi.inventory.aplication.usecase;

import com.nequi.inventory.domain.port.in.IdempotencyService;
import com.nequi.inventory.domain.port.out.IdempotencyRepository;
import com.nequi.inventory.domain.port.out.RedisIdempotency;
import com.nequi.inventory.domain.valueobject.OrderId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {
    private final RedisIdempotency redis;
    private final IdempotencyRepository dynamo;

    @Override
    public Mono<Boolean> tryProcess(OrderId key) {

        return redis.exists(key)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.just(false);
                    }

                    return dynamo.saveIfNotExists(key)
                            .flatMap(saved -> {
                                if (!saved) return Mono.just(false);

                                return redis.save(key)
                                        .thenReturn(true);
                            });
                });
    }
}
