package com.nequi.inventory.infrastructure.persistence.mapper;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.TicketEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.Instant;

@Mapper(componentModel = "spring")
public interface TicketEntityMapper {

    @Mapping(target = "ticketId",  expression = "java(ticket.getTicketId().value())")
    @Mapping(target = "orderId",  expression = "java(ticket.getOrderId.value())")
    @Mapping(target = "eventId",   expression = "java(ticket.getEventId().value())")
    @Mapping(target = "createdAt", expression = "java(ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : null)")
    @Mapping(target = "updatedAt", expression = "java(ticket.getUpdatedAt() != null ? ticket.getUpdatedAt().toString() : null)")
    TicketEntity toEntity(Ticket ticket);

    default Ticket toDomain(TicketEntity entity) {
        if (entity == null) return null;

        return Ticket.reconstitute(
                new TicketId(entity.getTicketId()),
                new EventId(entity.getEventId()),
                entity.getStatus(),
                entity.getUserId(),
                entity.getOrderId(),
                entity.getExpiresAt(),
                entity.getVersion(),
                entity.getCreatedAt() != null ? Instant.parse(entity.getCreatedAt()) : null,
                entity.getUpdatedAt() != null ? Instant.parse(entity.getUpdatedAt()) : null
        );
    }
}