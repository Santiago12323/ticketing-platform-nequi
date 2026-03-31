package com.nequi.ticketing_service.infrastructure.persistence.mapper;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    @Mapping(target = "id", source = "order.id.value")
    @Mapping(target = "eventId", source = "order.eventId.value")
    @Mapping(target = "userId", source = "order.userId.value")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "amount", source = "order.totalPrice.amount")
    @Mapping(target = "currency", source = "order.totalPrice.currency")
    @Mapping(target = "seatIds", source = "seatIds")
    @Mapping(target = "createdAt", expression = "java(order.getCreatedAt().toString())")
    @Mapping(target = "updatedAt", expression = "java(order.getUpdatedAt().toString())")
    OrderEntity toEntity(Order order, List<String> seatIds);

    @Mapping(target = "id", expression = "java(OrderId.of(entity.getId()))")
    Order toDomain(OrderEntity entity);
}
