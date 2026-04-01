package com.nequi.ticketing_service.infrastructure.web.handler;

import com.nequi.ticketing_service.domain.port.in.OrderUseCase;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.web.dto.request.CreateOrderRequest;
import com.nequi.ticketing_service.infrastructure.web.dto.response.CreateOrderResponse;
import com.nequi.ticketing_service.infrastructure.web.dto.response.OrderStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OrderHandler {

    private final OrderUseCase orderUseCase;

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateOrderRequest.class)
                .flatMap(dto -> orderUseCase.create(
                        UserId.of(dto.userId()),
                        EventId.of(dto.eventId()),
                        Money.of(dto.totalPrice(), dto.currency()),
                        dto.seatIds()
                ))
                .flatMap(orderId -> ServerResponse.accepted()
                        .bodyValue(new CreateOrderResponse(orderId.value(), "PENDING_PROCESSING")));
    }

    public Mono<ServerResponse> getStatus(ServerRequest request) {
        String id = request.pathVariable("id");

        return orderUseCase.getById(OrderId.of(id))
                .flatMap(order -> ServerResponse.ok()
                        .bodyValue(new OrderStatusResponse(
                                order.getId().value(),
                                order.getStatus().name(),
                                order.getUpdatedAt()
                        )));
    }
}