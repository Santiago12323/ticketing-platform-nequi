package com.nequi.inventory.infrastructure.web.route;

import com.nequi.inventory.infrastructure.web.handler.EventHandler;
import com.nequi.inventory.infrastructure.web.handler.TicketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class EventRouter {

    @Bean
    public RouterFunction<ServerResponse> eventRoutes(EventHandler handler) {
        return route()
                .GET("/tickets/stream/available/{eventId}", handler::getAvailableTicketsByEvent)
                .GET("/tickets/stream/{eventId}",           handler::getTicketsByEvent)
                .GET("/events/{id}",    handler::getEvent)
                .GET("/events",         handler::getAllEvents)
                .POST("/events",        handler::createEvent)
                .DELETE("/events/{id}", handler::deleteEvent)
                .build();
    }
}