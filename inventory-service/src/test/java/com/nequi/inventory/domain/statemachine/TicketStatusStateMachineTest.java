package com.nequi.inventory.domain.statemachine;

import com.nequi.inventory.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TicketStatusStateMachineTest {

    @ParameterizedTest(name = "Desde {0} con {1} -> Debe ir a {2}")
    @MethodSource("provideValidTransitions")
    @DisplayName("Transiciones validas de negocio")
    void shouldAllowValidTransitions(TicketStatus initialState, TicketEvent event, TicketStatus expectedState) {
        TicketStatus result = initialState.transition(event);
        assertEquals(expectedState, result);
    }

    @ParameterizedTest(name = "Desde {0} con {1} -> Debe ser RECHAZADO")
    @MethodSource("provideInvalidTransitions")
    @DisplayName("Lanzar BusinessException para transiciones prohibidas")
    void shouldRejectInvalidTransitions(TicketStatus initialState, TicketEvent event) {
        String expectedErrorCode = "TICKET_EVENT_NOT_ACCEPTED";

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            initialState.transition(event);
        });

        assertEquals(expectedErrorCode, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(event.toString()));
    }

    @Test
    @DisplayName("Identificacion correcta de estados finales")
    void shouldIdentifyFinalStates() {
        assertAll(
                () -> assertTrue(TicketStatus.SOLD.isFinalState()),
                () -> assertTrue(TicketStatus.COMPLIMENTARY.isFinalState()),
                () -> assertFalse(TicketStatus.AVAILABLE.isFinalState()),
                () -> assertFalse(TicketStatus.RESERVED.isFinalState()),
                () -> assertFalse(TicketStatus.PENDING_CONFIRMATION.isFinalState())
        );
    }

    private static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
                Arguments.of(TicketStatus.AVAILABLE, TicketEvent.RESERVE, TicketStatus.RESERVED),
                Arguments.of(TicketStatus.AVAILABLE, TicketEvent.ASSIGN_COMPLIMENTARY, TicketStatus.COMPLIMENTARY),

                Arguments.of(TicketStatus.RESERVED, TicketEvent.START_PAYMENT, TicketStatus.PENDING_CONFIRMATION),
                Arguments.of(TicketStatus.RESERVED, TicketEvent.CONFIRM_PAYMENT, TicketStatus.SOLD), // <-- Nueva transicion valida
                Arguments.of(TicketStatus.RESERVED, TicketEvent.CANCEL_RESERVATION, TicketStatus.AVAILABLE),
                Arguments.of(TicketStatus.RESERVED, TicketEvent.EXPIRE_RESERVATION, TicketStatus.AVAILABLE),

                Arguments.of(TicketStatus.PENDING_CONFIRMATION, TicketEvent.CONFIRM_PAYMENT, TicketStatus.SOLD),
                Arguments.of(TicketStatus.PENDING_CONFIRMATION, TicketEvent.FAIL_PAYMENT, TicketStatus.AVAILABLE),
                Arguments.of(TicketStatus.PENDING_CONFIRMATION, TicketEvent.EXPIRE_RESERVATION, TicketStatus.AVAILABLE)
        );
    }

    private static Stream<Arguments> provideInvalidTransitions() {
        return Stream.of(
                Arguments.of(TicketStatus.AVAILABLE, TicketEvent.CONFIRM_PAYMENT),
                Arguments.of(TicketStatus.AVAILABLE, TicketEvent.FAIL_PAYMENT),

                Arguments.of(TicketStatus.SOLD, TicketEvent.RESERVE),
                Arguments.of(TicketStatus.SOLD, TicketEvent.CANCEL_RESERVATION),

                Arguments.of(TicketStatus.COMPLIMENTARY, TicketEvent.START_PAYMENT),

                Arguments.of(TicketStatus.PENDING_CONFIRMATION, TicketEvent.RESERVE)
        );
    }
}