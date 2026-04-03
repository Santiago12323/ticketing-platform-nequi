package com.nequi.inventory.infrastructure.persistence.mapper;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import com.nequi.inventory.infrastructure.web.dto.Response.EventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "createdAt", expression = "java(event.getCreatedAt() != null ? event.getCreatedAt().toString() : null)")
    @Mapping(target = "updatedAt", expression = "java(event.getUpdatedAt() != null ? event.getUpdatedAt().toString() : null)")
    EventEntity toEntity(Event event);

    @Mapping(target = "createdAt", expression = "java(entity.getCreatedAt() != null ? java.time.Instant.parse(entity.getCreatedAt()) : null)")
    @Mapping(target = "updatedAt", expression = "java(entity.getUpdatedAt() != null ? java.time.Instant.parse(entity.getUpdatedAt()) : null)")
    Event toDomain(EventEntity entity);
}