package com.nequi.ticketing_service.infrastructure.web.handler;

import com.nequi.ticketing_service.domain.port.in.CreateOrderUseCase;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.web.dto.request.CreateOrderRequest;
import com.nequi.ticketing_service.infrastructure.web.dto.response.CreateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class OrderHandler {

    private final CreateOrderUseCase createOrderUseCase;

    @Autowired
    public OrderHandler(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateOrderRequest.class)
                .flatMap(dto -> {
                    UserId userId = UserId.of(dto.userId());
                    EventId eventId = EventId.of(dto.eventId());
                    Money totalPrice = Money.of(dto.totalPrice(), dto.currency());

                    return createOrderUseCase.execute(userId, eventId, totalPrice, dto.seatIds())
                            .flatMap(orderId -> {
                                CreateOrderResponse response =
                                        new CreateOrderResponse(orderId.value(), "RESERVED");
                                return ServerResponse.ok().bodyValue(response);
                            });
                });
    }

}
