package com.nequi.inventory.infrastructure.web.mapper;
import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.infrastructure.web.dto.Response.TicketResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketResponseMapper {

    @Mapping(target = "ticketId", expression = "java(ticket.getTicketId().value())")
    @Mapping(target = "eventId", source = "eventId")
    @Mapping(target = "status", expression = "java(ticket.getStatus())")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "isFinalState", expression = "java(ticket.isFinal())")
    TicketResponse toResponse(Ticket ticket);
}