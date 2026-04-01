package com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.enums;

public enum InventoryErrorCode {
    INSUFFICIENT_STOCK,
    SEATS_ALREADY_TAKEN,
    EVENT_NOT_FOUND,
    INTERNAL_ERROR
}