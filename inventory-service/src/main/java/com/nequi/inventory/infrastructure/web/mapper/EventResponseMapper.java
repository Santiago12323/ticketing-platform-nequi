package com.nequi.inventory.infrastructure.web.mapper;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.infrastructure.web.dto.Response.EventResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventResponseMapper {
    EventResponse toResponse(Event event);
}