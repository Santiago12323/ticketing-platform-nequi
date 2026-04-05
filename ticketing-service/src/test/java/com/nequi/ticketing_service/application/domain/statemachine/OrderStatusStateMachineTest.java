package com.nequi.ticketing_service.application.domain.statemachine;

import com.nequi.ticketing_service.domain.exception.BusinessException;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.statemachine.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusStateMachineTest {

    static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.VALIDATION_SUCCESS, OrderStatus.PENDING_PAYMENT),
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.VALIDATION_FAILED,  OrderStatus.FAILED_VALIDATION),
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.EXPIRE,             OrderStatus.EXPIRED),
                Arguments.of(OrderStatus.PENDING_PAYMENT,    OrderEvent.CONFIRM_PAYMENT,    OrderStatus.PAID),
                Arguments.of(OrderStatus.PENDING_PAYMENT,    OrderEvent.CANCEL,             OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.PENDING_PAYMENT,    OrderEvent.EXPIRE,             OrderStatus.EXPIRED)
        );
    }

    static Stream<Arguments> provideInvalidTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PAID,              OrderEvent.VALIDATION_SUCCESS),
                Arguments.of(OrderStatus.PAID,              OrderEvent.CANCEL),
                Arguments.of(OrderStatus.CANCELLED,         OrderEvent.EXPIRE),
                Arguments.of(OrderStatus.EXPIRED,           OrderEvent.CONFIRM_PAYMENT),
                Arguments.of(OrderStatus.FAILED_VALIDATION, OrderEvent.CONFIRM_PAYMENT),
                Arguments.of(OrderStatus.PENDING_VALIDATION, OrderEvent.CONFIRM_PAYMENT),
                Arguments.of(OrderStatus.PENDING_PAYMENT,   OrderEvent.VALIDATION_FAILED)
        );
    }

    @ParameterizedTest(name = "State {0} + Event {1} -> {2}")
    @MethodSource("provideValidTransitions")
    @DisplayName("Should transition correctly for valid events")
    void shouldAcceptValidTransitions(OrderStatus initialState, OrderEvent event, OrderStatus expectedState) {
        OrderStatus result = initialState.transition(event);
        assertEquals(expectedState, result,
                "Expected state after event " + event + " to be " + expectedState);
    }

    @ParameterizedTest(name = "State {0} + Event {1} -> Should throw BusinessException")
    @MethodSource("provideInvalidTransitions")
    @DisplayName("Should throw BusinessException for invalid transitions")
    void shouldRejectInvalidTransitions(OrderStatus initialState, OrderEvent event) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> initialState.transition(event));

        if (initialState.isFinalState()) {
            assertEquals("ORDER_FINAL_STATE", exception.getErrorCode(),
                    "Expected error code ORDER_FINAL_STATE for final state " + initialState);
            assertTrue(exception.getMessage().contains(initialState.name()),
                    "Exception message should contain the final state name");
        } else {
            assertEquals("ORDER_EVENT_NOT_ACCEPTED", exception.getErrorCode(),
                    "Expected error code ORDER_EVENT_NOT_ACCEPTED for state " + initialState);
            assertTrue(exception.getMessage().contains(event.name()),
                    "Exception message should contain the event name");
        }
    }
}