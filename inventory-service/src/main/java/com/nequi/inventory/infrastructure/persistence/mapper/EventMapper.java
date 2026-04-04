package com.nequi.inventory.infrastructure.persistence.mapper;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "eventId",   expression = "java(event.getEventId().value())")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    EventEntity toEntity(Event event);

    @Mapping(target = "eventId",   expression = "java(com.nequi.inventory.domain.valueobject.EventId.of(entity.getEventId()))")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    Event toDomain(EventEntity entity);
}