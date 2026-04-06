package com.nequi.ticketing_service.application.infrastruture.web.router;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.in.OrderUseCase;
import com.nequi.ticketing_service.domain.port.out.OrderHistoryService;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.web.dto.request.ConfirmPaymentRequest;
import com.nequi.ticketing_service.infrastructure.web.dto.request.CreateOrderRequest;
import com.nequi.ticketing_service.infrastructure.web.handler.OrderHandler;
import com.nequi.ticketing_service.infrastructure.web.router.OrderRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest
@ContextConfiguration(classes = {OrderRouter.class, OrderHandler.class})
class OrderRouterTest {

    @Autowired
    private WebTestClient webTestClient;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private OrderUseCase orderUseCase;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private OrderHistoryService historyService;

    private String orderId;
    private String userId;
    private String eventId;
    private String seatId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        eventId = UUID.randomUUID().toString();
        seatId = UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("AAA - POST /orders: Should create order successfully")
    void createOrder_Success() {
        CreateOrderRequest request = new CreateOrderRequest(userId, eventId, 50.0, "COP", List.of(seatId));

        when(orderUseCase.create(any(), any(), any(), any()))
                .thenReturn(Mono.just(OrderId.of(orderId)));

        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.status").isEqualTo("PENDING_PROCESSING");
    }

    @Test
    @DisplayName("AAA - POST /orders/confirm: Should confirm payment successfully")
    void confirmPayment_Success() {

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                userId,
                eventId,
                50.0,
                "COP",
                List.of(seatId),
                orderId
        );

        when(orderUseCase.confirmAll(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(OrderId.of(orderId)));

        webTestClient.post()
                .uri("/orders/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.status").isEqualTo("PAYMENT_IN_PROGRESS");
    }

    @Test
    @DisplayName("AAA - GET /orders/{id}: Should return order status")
    void getStatus_Success() {
        Order mockOrder = Order.create(
                OrderId.of(orderId),
                UserId.of(userId),
                EventId.of(eventId),
                Money.of(50.0, "COP"),
                List.of(TicketId.of(seatId))
        );

        when(orderUseCase.getById(any())).thenReturn(Mono.just(mockOrder));

        webTestClient.get()
                .uri("/orders/{id}", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.status").exists();
    }
}