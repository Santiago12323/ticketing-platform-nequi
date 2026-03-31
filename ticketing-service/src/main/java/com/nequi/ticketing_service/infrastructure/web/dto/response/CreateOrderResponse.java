package com.nequi.ticketing_service.infrastructure.web.dto.response;

public record CreateOrderResponse(
        String orderId,
        String status
) {}
