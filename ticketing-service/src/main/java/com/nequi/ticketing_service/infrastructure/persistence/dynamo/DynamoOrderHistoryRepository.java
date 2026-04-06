package com.nequi.ticketing_service.infrastructure.persistence.dynamo;

import com.nequi.ticketing_service.domain.port.out.OrderHistoryRepository;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderHistoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoOrderHistoryRepository implements OrderHistoryRepository {

    private final DynamoDbAsyncTable<OrderHistoryEntity> historyTable;

    @Override
    public Mono<Void> saveHistory(String orderId, String fromStatus, String toStatus, String details) {
        OrderHistoryEntity history = OrderHistoryEntity.builder()
                .orderId(orderId)
                .createdAt(Instant.now().toString())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .details(details)
                .build();

        return Mono.fromFuture(historyTable.putItem(history))
                .doOnSuccess(v -> log.info("[AUDIT] Historial registrado..."))
                .doOnError(e -> log.error("[AUDIT ERROR] No se pudo guardar...", e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    @Override
    public Flux<OrderHistoryEntity> findByOrderId(String orderId) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(orderId).build());
        return Flux.from(historyTable.query(r -> r.queryConditional(queryConditional)))
                .flatMapIterable(page -> page.items())
                .doOnError(e -> log.error("[AUDIT ERROR] Error consultando historial de orden {}", orderId, e));
    }
}