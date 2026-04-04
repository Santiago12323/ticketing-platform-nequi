package com.nequi.ticketing_service.infrastructure.persistence.dynamo;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import com.nequi.ticketing_service.infrastructure.persistence.mapper.OrderEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;

@Slf4j
@Repository
public class DynamoOrderRepository implements OrderRepository {

    private final DynamoDbAsyncTable<OrderEntity> orderTable;
    private final OrderEntityMapper mapper;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    public DynamoOrderRepository(DynamoDbAsyncTable<OrderEntity> orderTable,
                                 OrderEntityMapper mapper) {
        this.orderTable = orderTable;
        this.mapper = mapper;
    }

    @Override
    public Mono<Order> save(Order order) {
        OrderEntity entity = mapper.toEntity(order);
        return Mono.fromFuture(orderTable.putItem(entity))
                .doOnSuccess(v -> logAudit("Order {} saved in DynamoDB", order.getId().value()))
                .thenReturn(order);
    }

    @Override
    public Mono<Order> updateStatus(Order order) {
        OrderEntity entity = mapper.toEntity(order);

        return Mono.fromFuture(orderTable.updateItem(entity))
                .doOnSuccess(v -> logAudit("Order {} status updated to {} in DynamoDB",
                        order.getId().value(), order.getStatus()))
                .thenReturn(order)
                .onErrorMap(e -> new RuntimeException("Error updating order status: " + order.getId().value(), e));
    }

    @Override
    public Mono<Order> findById(OrderId id) {
        Key key = Key.builder().partitionValue(id.value()).build();

        return Mono.fromFuture(orderTable.getItem(GetItemEnhancedRequest.builder().key(key).build()))
                .map(mapper::toDomain)
                .doOnSuccess(order -> {
                    if (order != null) logAudit("Order {} retrieved from DynamoDB", id.value());
                });
    }

    private void logAudit(String format, Object... args) {
        if (auditEnabled) log.info(format, args);
    }
}