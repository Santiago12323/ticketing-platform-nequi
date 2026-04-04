package com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.response;


import com.nequi.ticketing_service.domain.valueobject.OrderId;

import java.util.List;

public record InventoryResponse(
        OrderId orderId,
        boolean success,
        List<String> failedTicketIds
) {
    public static InventoryResponse success(OrderId orderId) {
        return new InventoryResponse(orderId, true, List.of());
    }

    public static InventoryResponse failure(OrderId orderId, java.util.List<String> failed) {
        return new InventoryResponse(orderId, false, failed);
    }
}
