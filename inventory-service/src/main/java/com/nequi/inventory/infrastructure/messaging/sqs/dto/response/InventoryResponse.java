package com.nequi.inventory.infrastructure.messaging.sqs.dto.response;


public record InventoryResponse(
        String orderId,
        boolean success,
        java.util.List<String> failedTicketIds
) {
    public static InventoryResponse success(String orderId) {
        return new InventoryResponse(orderId, true, java.util.List.of());
    }

    public static InventoryResponse failure(String orderId, java.util.List<String> failed) {
        return new InventoryResponse(orderId, false, failed);
    }
}