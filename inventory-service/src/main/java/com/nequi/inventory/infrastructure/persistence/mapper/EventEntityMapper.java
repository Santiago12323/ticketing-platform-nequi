package com.nequi.inventory.infrastructure.persistence.mapper;

import com.nequi.inventory.domain.model.event.Event;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface EventEntityMapper {

    EventEntityMapper INSTANCE = Mappers.getMapper(EventEntityMapper.class);

    @Mapping(target = "eventId", source = "id.value")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "capacity", source = "capacity")
    @Mapping(target = "availableSeats", expression = "java(event.getAvailableCount())")
    @Mapping(target = "reservedSeats", expression = "java(event.getReservedSeats().size())")
    @Mapping(target = "soldSeats", expression = "java(event.getSoldSeats().size())")
    @Mapping(target = "version", source = "version")
    EventEntity toEntity(Event event);

    @Mapping(target = "id", expression = "java(new EventId(entity.getEventId()))")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "capacity", source = "capacity")
    Event toDomain(EventEntity entity);
}
