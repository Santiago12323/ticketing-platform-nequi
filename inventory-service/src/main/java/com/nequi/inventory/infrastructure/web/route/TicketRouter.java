package com.nequi.inventory.infrastructure.web.route;

import com.nequi.inventory.infrastructure.web.handler.TicketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class TicketRouter {

    @Bean
    public RouterFunction<ServerResponse> ticketRoutes(TicketHandler handler) {
        return route()
                .GET("/events/{eventId}/tickets/{ticketId}", handler::getTicket)
                .GET("/events/{eventId}/tickets/{ticketId}/status", handler::getTicketStatus)
                .build();
    }
}