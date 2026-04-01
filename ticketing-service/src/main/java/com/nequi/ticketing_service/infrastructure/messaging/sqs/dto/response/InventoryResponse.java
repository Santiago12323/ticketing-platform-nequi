package com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.response;

import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.enums.InventoryErrorCode;

import java.util.List;

public record InventoryResponse(
        String orderId,
        boolean success,
        List<String> confirmedSeatIds,
        InventoryErrorCode errorCode,
        String errorMessage,
        String inventoryTransactionId
) {
    public InventoryResponse {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is mandatory in InventoryResponse");
        }
    }

    public boolean hasError() {
        return !success;
    }
}