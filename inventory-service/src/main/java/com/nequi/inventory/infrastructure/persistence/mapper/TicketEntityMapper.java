package com.nequi.inventory.infrastructure.persistence.mapper;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.TicketEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketEntityMapper {

    @Mapping(target = "ticketId", expression = "java(ticket.getTicketId().value())")
    @Mapping(target = "eventId",  expression = "java(ticket.getEventId().value())")
    TicketEntity toEntity(Ticket ticket);
}