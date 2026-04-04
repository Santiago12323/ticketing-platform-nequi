package com.nequi.ticketing_service.infrastructure.persistence.mapper;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    @Mapping(target = "id", expression = "java(order.getId().value())")
    @Mapping(target = "eventId", expression = "java(order.getEventId().value())")
    @Mapping(target = "userId", expression = "java(order.getUserId().value())")
    @Mapping(target = "status", expression = "java(order.getStatus().name())")
    @Mapping(target = "amount", expression = "java(order.getTotalPrice().amount().doubleValue())")
    @Mapping(target = "currency", expression = "java(order.getTotalPrice().currency().getCurrencyCode())")
    @Mapping(target = "ticketIds", expression = "java(mapTicketIds(order.getTicketIds()))")
    @Mapping(target = "createdAt", expression = "java(order.getCreatedAt().toString())")
    @Mapping(target = "updatedAt", expression = "java(order.getUpdatedAt().toString())")
    OrderEntity toEntity(Order order);

    default Order toDomain(OrderEntity entity) {
        if (entity == null) return null;

        return Order.reconstruct(
                new OrderId(entity.getId()),
                new UserId(entity.getUserId()),
                new EventId(entity.getEventId()),
                Money.of(entity.getAmount(), entity.getCurrency()),
                OrderStatus.valueOf(entity.getStatus()),
                Instant.parse(entity.getCreatedAt()),
                Instant.parse(entity.getUpdatedAt()),
                mapToTicketIds(entity.getTicketIds())
        );
    }

    default List<String> mapTicketIds(List<TicketId> ticketIds) {
        if (ticketIds == null) return null;
        return ticketIds.stream()
                .map(TicketId::value)
                .toList();
    }

    default List<TicketId> mapToTicketIds(List<String> ticketIds) {
        if (ticketIds == null) return null;
        return ticketIds.stream()
                .map(TicketId::new)
                .toList();
    }
}