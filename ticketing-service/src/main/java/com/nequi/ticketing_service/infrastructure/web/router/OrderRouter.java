package com.nequi.ticketing_service.infrastructure.web.router;

import com.nequi.ticketing_service.infrastructure.web.handler.OrderHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class OrderRouter {

    @Bean
    public RouterFunction<ServerResponse> orderRoutes(OrderHandler handler) {
        return RouterFunctions.route()
                .POST("/orders", handler::create)
                .GET("/orders/{id}", handler::getStatus)
                .build();
    }
}