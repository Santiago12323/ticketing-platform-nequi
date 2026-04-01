package com.nequi.ticketing_service.infrastructure.persistence.dynamo;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import com.nequi.ticketing_service.infrastructure.persistence.factory.OrderFactory;
import com.nequi.ticketing_service.infrastructure.persistence.mapper.OrderEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.List;

@Slf4j
@Repository
public class DynamoOrderRepository implements OrderRepository {

    private final DynamoDbAsyncTable<OrderEntity> orderTable;
    private final OrderEntityMapper mapper;
    private final OrderFactory factory;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @Autowired
    public DynamoOrderRepository(DynamoDbAsyncTable<OrderEntity> orderTable,
                                 OrderEntityMapper mapper,
                                 OrderFactory factory) {
        this.orderTable = orderTable;
        this.mapper = mapper;
        this.factory = factory;
    }

    @Override
    public Mono<Order> save(Order order, List<String> seatIds) {
        OrderEntity entity = mapper.toEntity(order, seatIds);
        return Mono.fromFuture(orderTable.putItem(entity))
                .doOnSuccess(v -> logAudit("Order {} saved in DynamoDB", order.getId().value()))
                .thenReturn(order);
    }

    @Override
    public Mono<Order> updateStatus(Order order) {
        Key key = Key.builder().partitionValue(order.getId().value()).build();

        return Mono.fromFuture(orderTable.getItem(key))
                .flatMap(existingEntity -> {
                    if (existingEntity == null) {
                        return Mono.error(new RuntimeException("Cannot update status: Order not found"));
                    }

                    existingEntity.setStatus(order.getStatus().name());
                    existingEntity.setUpdatedAt(order.getUpdatedAt().toString());

                    return Mono.fromFuture(orderTable.updateItem(existingEntity))
                            .doOnSuccess(v -> logAudit("Order {} status updated to {}",
                                    order.getId().value(), order.getStatus()))
                            .thenReturn(order);
                });
    }

    @Override
    public Mono<Order> findById(OrderId id) {
        Key key = Key.builder().partitionValue(id.value()).build();
        return Mono.fromFuture(orderTable.getItem(key))
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + id.value())))
                .flatMap(factory::fromEntity);
    }

    private void logAudit(String format, Object... args) {
        if (auditEnabled) log.info(format, args);
    }
}