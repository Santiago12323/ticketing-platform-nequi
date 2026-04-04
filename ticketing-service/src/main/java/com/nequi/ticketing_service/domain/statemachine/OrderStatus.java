package com.nequi.ticketing_service.domain.statemachine;

import com.nequi.ticketing_service.domain.exception.BusinessException;

public enum OrderStatus {

    PENDING_VALIDATION {
        @Override
        public OrderStatus transition(OrderEvent event) {
            return switch (event) {
                case VALIDATION_SUCCESS -> PENDING_PAYMENT;
                case VALIDATION_FAILED  -> FAILED_VALIDATION;
                case EXPIRE             -> EXPIRED;
                default -> reject(event);
            };
        }
        @Override public boolean isExpirable() { return true; }
    },

    PENDING_PAYMENT {
        @Override
        public OrderStatus transition(OrderEvent event) {
            return switch (event) {
                case CONFIRM_PAYMENT -> PAID;
                case CANCEL          -> CANCELLED;
                case EXPIRE          -> EXPIRED;
                default -> reject(event);
            };
        }
        @Override public boolean isExpirable() { return true; }
    },

    PAID            { @Override public boolean isFinalState() { return true; } },
    CANCELLED       { @Override public boolean isFinalState() { return true; } },
    EXPIRED         { @Override public boolean isFinalState() { return true; } },
    FAILED_VALIDATION { @Override public boolean isFinalState() { return true; } };


    public OrderStatus transition(OrderEvent event) {
        if (isFinalState()) {
            throw new BusinessException(
                    "ORDER_FINAL_STATE",
                    "La orden está en estado final %s, no acepta eventos".formatted(this)
            );
        }
        return reject(event);
    }

    public boolean isFinalState() { return false; }
    public boolean isExpirable()  { return false; }

    protected OrderStatus reject(OrderEvent event) {
        throw new BusinessException(
                "ORDER_EVENT_NOT_ACCEPTED",
                "El evento %s no es válido para el estado %s".formatted(event, this)
        );
    }
}