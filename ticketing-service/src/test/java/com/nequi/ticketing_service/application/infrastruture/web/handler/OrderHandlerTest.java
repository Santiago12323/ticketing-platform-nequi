package com.nequi.ticketing_service.application.infrastruture.web.handler;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.in.OrderUseCase;
import com.nequi.ticketing_service.domain.valueobject.*;
import com.nequi.ticketing_service.infrastructure.web.dto.request.ConfirmPaymentRequest;
import com.nequi.ticketing_service.infrastructure.web.dto.request.CreateOrderRequest;
import com.nequi.ticketing_service.infrastructure.web.handler.OrderHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHandlerTest {

    @Mock
    private OrderUseCase orderUseCase;

    @InjectMocks
    private OrderHandler orderHandler;

    private String userId;
    private String eventId;
    private String orderId;
    private List<String> seatIds;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        eventId = UUID.randomUUID().toString();
        orderId = UUID.randomUUID().toString();
        seatIds = List.of(UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("AAA - Success: Should handle order creation")
    void create_Success() {
        // Arrange
        ServerRequest request = mock(ServerRequest.class);
        CreateOrderRequest dto = new CreateOrderRequest(userId, eventId, 100.0, "COP", seatIds);

        when(request.bodyToMono(CreateOrderRequest.class)).thenReturn(Mono.just(dto));
        when(orderUseCase.create(any(), any(), any(), any())).thenReturn(Mono.just(OrderId.of(orderId)));

        // Act
        Mono<ServerResponse> result = orderHandler.create(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(202, response.statusCode().value()); // Accepted
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("AAA - Success: Should handle payment confirmation")
    void confirmPayment_Success() {
        // Arrange
        ServerRequest request = mock(ServerRequest.class);
        ConfirmPaymentRequest dto = new ConfirmPaymentRequest(userId, eventId, 100.0, "COP", seatIds, orderId);

        when(request.bodyToMono(ConfirmPaymentRequest.class)).thenReturn(Mono.just(dto));
        when(orderUseCase.confirmAll(any(), any(), any(), any(), any())).thenReturn(Mono.just(OrderId.of(orderId)));

        // Act
        Mono<ServerResponse> result = orderHandler.confirmPayment(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.statusCode().value());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("AAA - Success: Should handle get status by ID")
    void getStatus_Success() {
        // Arrange
        ServerRequest request = mock(ServerRequest.class);
        Order mockOrder = Order.create(
                OrderId.of(orderId),
                UserId.of(userId),
                EventId.of(eventId),
                Money.of(100.0, "COP"),
                List.of(TicketId.of(seatIds.get(0)))
        );

        when(request.pathVariable("id")).thenReturn(orderId);
        when(orderUseCase.getById(any())).thenReturn(Mono.just(mockOrder));

        // Act
        Mono<ServerResponse> result = orderHandler.getStatus(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.statusCode().value()); // OK
                })
                .verifyComplete();
    }
}