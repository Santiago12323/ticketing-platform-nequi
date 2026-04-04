package com.nequi.inventory.infrastructure.web.mapper;

import com.nequi.inventory.domain.model.Ticket;
import com.nequi.inventory.infrastructure.web.dto.Response.TicketResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketResponseMapper {

    @Mapping(target = "isFinalState", expression = "java(ticket.isFinal())")
    @Mapping(target = "status",       expression = "java(ticket.getStatus())")
    TicketResponse toResponse(Ticket ticket);
}