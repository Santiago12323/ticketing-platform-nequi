package com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.response;

import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.enums.Type;

import java.util.List;

public record InventoryResponse(
        String orderId,
        boolean success,
        List<String> failedTicketId,
        Type type
) {
    public InventoryResponse {
        failedTicketId = (failedTicketId == null) ? List.of() : failedTicketId;
    }

    public static InventoryResponse success(String orderId, Type type) {
        return new InventoryResponse(orderId, true, List.of(), type);
    }

    public static InventoryResponse failure(String orderId, List<String> failed, Type type) {
        return new InventoryResponse(orderId, false, failed, type);
    }
}