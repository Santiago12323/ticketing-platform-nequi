package com.nequi.ticketing_service.application.domain.modelo;

import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import com.nequi.ticketing_service.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private OrderId orderId;
    private UserId userId;
    private EventId eventId;
    private Money totalPrice;
    private List<TicketId> ticketIds;

    @BeforeEach
    void setUp() {
        orderId = new OrderId(UUID.randomUUID().toString());
        userId = new UserId(UUID.randomUUID().toString());
        eventId = new EventId(UUID.randomUUID().toString());
        totalPrice = Money.of(50000, "COP");
        ticketIds = List.of(new TicketId(UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("GIVEN valid data WHEN creating order THEN status should be PENDING_VALIDATION")
    void createOrder_Success() {
        // Act
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);

        // Assert
        assertAll(
                () -> assertEquals(orderId, order.getId()),
                () -> assertEquals(OrderStatus.PENDING_VALIDATION, order.getStatus()),
                () -> assertNotNull(order.getCreatedAt()),
                () -> assertEquals(order.getCreatedAt(), order.getUpdatedAt())
        );
    }

    @Test
    @DisplayName("GIVEN pending order WHEN inventory is confirmed THEN status updates to PENDING_PAYMENT")
    void confirmInventory_UpdatesStatus() throws InterruptedException {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);
        Instant firstUpdate = order.getUpdatedAt();
        Thread.sleep(2);

        // Act
        order.confirmInventory();

        // Assert
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertTrue(order.getUpdatedAt().isAfter(firstUpdate));
    }

    @Test
    @DisplayName("GIVEN pending payment order WHEN payment fails THEN status should be FAILED_PAYMENT")
    void failPayment_TransitionsToFailed() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);
        order.confirmInventory();

        // Act
        order.failPayment();

        // Assert
        assertEquals(OrderStatus.FAILED_PAYMENT, order.getStatus());
        assertTrue(order.isFinal());
    }

    @Test
    @DisplayName("GIVEN order in final state WHEN checking isFinal THEN return true")
    void isFinal_CorrectBehavior() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);

        // Act & Assert
        assertFalse(order.isFinal(), "Pending order should not be final");

        order.cancel();
        assertTrue(order.isFinal(), "Cancelled order should be final");
    }

    @Test
    @DisplayName("GIVEN inventory fails WHEN calling failInventory THEN status should be FAILED_VALIDATION")
    void failInventory_TransitionsToFailedValidation() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);

        // Act
        order.failInventory();

        // Assert
        assertEquals(OrderStatus.FAILED_VALIDATION, order.getStatus());
        assertTrue(order.isFinal());
    }

    @Test
    @DisplayName("GIVEN existing data WHEN reconstructing order THEN preserve all original values")
    void reconstruct_PreservesState() {
        // Arrange
        Instant past = Instant.now().minusSeconds(3600);

        // Act
        Order order = Order.reconstruct(orderId, userId, eventId, totalPrice,
                OrderStatus.PAID, past, past, ticketIds);

        // Assert
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(past, order.getCreatedAt());
        assertTrue(order.isFinal());
    }

    @Test
    @DisplayName("GIVEN pending order WHEN cancelled THEN status should be CANCELLED")
    void cancel_TransitionsToCancelled() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);

        // Act
        order.cancel();

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.isFinal());
    }

    @Test
    @DisplayName("GIVEN pending order WHEN expired THEN status should be EXPIRED")
    void expire_TransitionsToExpired() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);

        // Act
        order.expire();

        // Assert
        assertEquals(OrderStatus.EXPIRED, order.getStatus());
        assertTrue(order.isFinal());
    }

    @Test
    @DisplayName("GIVEN pending payment order WHEN confirmed THEN status should be PAID")
    void confirmPayment_TransitionsToPaid() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);
        order.confirmInventory();

        // Act
        order.confirmPayment();

        // Assert
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertTrue(order.isFinal());
    }

    @Test
    @DisplayName("GIVEN pending payment order WHEN calling pay THEN status should be PAID")
    void pay_TransitionsToPaid() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);
        order.confirmInventory();

        // Act
        order.pay();

        // Assert
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    @DisplayName("GIVEN order in final state WHEN applying new event THEN throw BusinessException")
    void applyEvent_ThrowsExceptionInFinalState() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);
        order.cancel();

        // Act & Assert
        assertThrows(com.nequi.ticketing_service.domain.exception.BusinessException.class, () -> {
            order.confirmInventory();
        }, "Debería fallar porque la orden ya está en un estado final");
    }

    @Test
    @DisplayName("GIVEN invalid event for current state WHEN applying event THEN throw BusinessException")
    void applyEvent_ThrowsExceptionForInvalidTransition() {
        // Arrange
        Order order = Order.create(orderId, userId, eventId, totalPrice, ticketIds);
        // Act & Assert
        assertThrows(com.nequi.ticketing_service.domain.exception.BusinessException.class, () -> {
            order.confirmPayment();
        }, "Debería fallar porque no se puede pagar sin validar inventario");
    }
}