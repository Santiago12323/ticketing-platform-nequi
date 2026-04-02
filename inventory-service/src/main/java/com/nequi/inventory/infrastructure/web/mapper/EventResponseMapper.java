package com.nequi.inventory.infrastructure.web.mapper;


import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.infrastructure.web.dto.Response.EventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventResponseMapper {

    @Mapping(target = "eventId", source = "eventId")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "totalCapacity", source = "totalCapacity")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    EventResponse toResponse(Event event);
}