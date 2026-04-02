package com.nequi.inventory.infrastructure.persistence.mapper;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import com.nequi.inventory.infrastructure.web.dto.Response.EventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "eventId", source = "eventId")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "totalCapacity", source = "totalCapacity")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", expression = "java(event.getCreatedAt().toString())")
    @Mapping(target = "updatedAt", expression = "java(event.getUpdatedAt().toString())")
    EventEntity toEntity(Event event);

    @Mapping(target = "eventId", source = "eventId")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "totalCapacity", source = "totalCapacity")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.parse(entity.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.parse(entity.getUpdatedAt()))")
    Event toDomain(EventEntity entity);

}