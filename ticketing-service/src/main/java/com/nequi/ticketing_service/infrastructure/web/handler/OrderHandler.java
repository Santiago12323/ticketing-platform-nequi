package com.nequi.ticketing_service.infrastructure.web.handler;

import com.nequi.ticketing_service.domain.port.in.OrderUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderHistoryService;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderHistoryEntity;
import com.nequi.ticketing_service.infrastructure.web.dto.request.ConfirmPaymentRequest;
import com.nequi.ticketing_service.infrastructure.web.dto.request.CreateOrderRequest;
import com.nequi.ticketing_service.infrastructure.web.dto.response.CreateOrderResponse;
import com.nequi.ticketing_service.infrastructure.web.dto.response.OrderStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderHandler {

    private final OrderUseCase orderUseCase;
    private final OrderHistoryService historyService;

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

    public Mono<ServerResponse> confirmPayment(ServerRequest request) {
        return request.bodyToMono(ConfirmPaymentRequest.class)
                .flatMap(dto -> orderUseCase.confirmAll(
                        UserId.of(dto.userId()),
                        EventId.of(dto.eventId()),
                        Money.of(dto.amount(), dto.currency()),
                        dto.seatIds(),
                        OrderId.of(dto.orderId())
                ))
                .flatMap(orderId -> ServerResponse.ok()
                        .bodyValue(new CreateOrderResponse(orderId.value(), "PAYMENT_IN_PROGRESS")));
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

    public Mono<ServerResponse> getHistoryStream(ServerRequest request) {
        String orderId = request.pathVariable("id");

        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(historyService.getHistory(OrderId.of(orderId)), OrderHistoryEntity.class);
    }
}