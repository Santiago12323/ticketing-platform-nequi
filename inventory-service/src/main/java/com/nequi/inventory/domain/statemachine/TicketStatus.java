package com.nequi.inventory.domain.statemachine;

import com.nequi.inventory.domain.exception.BusinessException;

public enum TicketStatus {

    AVAILABLE {
        @Override
        public TicketStatus transition(TicketEvent event) {
            return switch (event) {
                case RESERVE              -> RESERVED;
                case ASSIGN_COMPLIMENTARY -> COMPLIMENTARY;
                default -> reject(event);
            };
        }
    },

    RESERVED {
        @Override
        public TicketStatus transition(TicketEvent event) {
            return switch (event) {
                case START_PAYMENT                      -> PENDING_CONFIRMATION;
                case CANCEL_RESERVATION,
                     EXPIRE_RESERVATION                 -> AVAILABLE;
                default -> reject(event);
            };
        }
    },

    PENDING_CONFIRMATION {
        @Override
        public TicketStatus transition(TicketEvent event) {
            return switch (event) {
                case CONFIRM_PAYMENT                    -> SOLD;
                case FAIL_PAYMENT,
                     EXPIRE_RESERVATION                 -> AVAILABLE;
                default -> reject(event);
            };
        }
    },

    SOLD {
        @Override
        public TicketStatus transition(TicketEvent event) {
            return reject(event);
        }

        @Override
        public boolean isFinalState() { return true; }
    },

    COMPLIMENTARY {
        @Override
        public TicketStatus transition(TicketEvent event) {
            return reject(event);
        }

        @Override
        public boolean isFinalState() { return true; }
    };

    public abstract TicketStatus transition(TicketEvent event);

    public boolean isFinalState() { return false; }

    protected TicketStatus reject(TicketEvent event) {
        throw new BusinessException(
                "TICKET_EVENT_NOT_ACCEPTED",
                "Evento %s no válido para estado %s".formatted(event, this)
        );
    }
}