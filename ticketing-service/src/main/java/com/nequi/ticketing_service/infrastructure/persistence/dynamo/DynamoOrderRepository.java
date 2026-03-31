package com.nequi.ticketing_service.infrastructure.persistence.dynamo;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import com.nequi.ticketing_service.infrastructure.persistence.mapper.OrderEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.List;

@Repository
public class DynamoOrderRepository implements OrderRepository {

    private final DynamoDbAsyncTable<OrderEntity> orderTable;
    private final OrderEntityMapper mapper;

    @Autowired
    public DynamoOrderRepository(DynamoDbAsyncTable<OrderEntity> orderTable,
                                 OrderEntityMapper mapper) {
        this.orderTable = orderTable;
        this.mapper = mapper;
    }


    @Override
    public Mono<Order> save(Order order, List<String> seatIds) {
        OrderEntity entity = mapper.toEntity(order, seatIds);
        return Mono.fromFuture(orderTable.putItem(entity))
                .thenReturn(order);
    }

    @Override
    public Mono<Order> findById(OrderId id) {
        Key key = Key.builder().partitionValue(id.value()).build();
        return Mono.fromFuture(orderTable.getItem(key))
                .flatMap(entity -> {
                    if (entity == null) {
                        return Mono.error(new RuntimeException("Order not found"));
                    }
                    return Mono.just(mapper.toDomain(entity));
                });
    }
}
