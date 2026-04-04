package com.nequi.ticketing_service.application.domain.statemachine;

import com.nequi.ticketing_service.domain.exception.BusinessException;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusStateMachineTest {

    @ParameterizedTest(name = "State {0} + Event {1} -> Should result in {2}")
    @MethodSource("provideValidTransitions")
    @DisplayName("Should allow valid order business transitions")
    void shouldAllowValidTransitions(OrderStatus initialState, OrderEvent event, OrderStatus expectedState) {
        // Act
        OrderStatus result = initialState.transition(event);

        // Assert
        assertEquals(expectedState, result);
    }

    @ParameterizedTest(name = "State {0} + Event {1} -> Should be REJECTED")
    @MethodSource("provideInvalidTransitions")
    @DisplayName("Should throw BusinessException for forbidden order transitions")
    void shouldRejectInvalidTransitions(OrderStatus initialState, OrderEvent event) {
        // Arrange
        String expectedErrorCode = "ORDER_EVENT_NOT_ACCEPTED";

        // Act
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            initialState.transition(event);
        });

        // Assert
        assertEquals(expectedErrorCode, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(event.name()));
    }

    @Test
    @DisplayName("Should correctly identify final order states")
    void shouldIdentifyFinalStates() {
        // Act & Assert
        assertAll(
                () -> assertTrue(OrderStatus.PAID.isFinalState()),
                () -> assertTrue(OrderStatus.CANCELLED.isFinalState()),
                () -> assertTrue(OrderStatus.EXPIRED.isFinalState()),
                () -> assertTrue(OrderStatus.FAILED_VALIDATION.isFinalState()),
                () -> assertFalse(OrderStatus.PENDING_VALIDATION.isFinalState()),
                () -> assertFalse(OrderStatus.PENDING_PAYMENT.isFinalState())
        );
    }

    private static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.VALIDATION_SUCCESS, OrderStatus.PENDING_PAYMENT),
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.VALIDATION_FAILED, OrderStatus.FAILED_VALIDATION),
                Arguments.of(OrderStatus.PENDING_PAYMENT, OrderEvent.CONFIRM_PAYMENT, OrderStatus.PAID),
                Arguments.of(OrderStatus.PENDING_PAYMENT, OrderEvent.CANCEL, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.PENDING_PAYMENT, OrderEvent.EXPIRE, OrderStatus.EXPIRED)
        );
    }

    private static Stream<Arguments> provideInvalidTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.CONFIRM_PAYMENT),
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.CANCEL),
                Arguments.of(OrderStatus.PAID, OrderEvent.VALIDATION_SUCCESS),
                Arguments.of(OrderStatus.PAID, OrderEvent.CANCEL),
                Arguments.of(OrderStatus.FAILED_VALIDATION, OrderEvent.CONFIRM_PAYMENT),
                Arguments.of(OrderStatus.PENDING_PAYMENT, OrderEvent.VALIDATION_SUCCESS),
                Arguments.of(OrderStatus.EXPIRED, OrderEvent.CONFIRM_PAYMENT)
        );
    }
}