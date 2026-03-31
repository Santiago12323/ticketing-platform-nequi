package com.nequi.ticketing_service.infrastructure.persistence.mapper;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    @Mapping(target = "id", expression = "java(order.getId().value())")
    @Mapping(target = "eventId", expression = "java(order.getEventId().value())")
    @Mapping(target = "userId", expression = "java(order.getUserId().value())")
    @Mapping(target = "status", expression = "java(order.getStatus().name())")
    @Mapping(target = "amount", expression = "java(order.getTotalPrice().amount().doubleValue())")
    @Mapping(target = "currency", expression = "java(order.getTotalPrice().currency().getCurrencyCode())")
    @Mapping(target = "seatIds", source = "seatIds")
    @Mapping(target = "createdAt", expression = "java(order.getCreatedAt().toString())")
    @Mapping(target = "updatedAt", expression = "java(order.getUpdatedAt().toString())")
    OrderEntity toEntity(Order order, List<String> seatIds);
}
