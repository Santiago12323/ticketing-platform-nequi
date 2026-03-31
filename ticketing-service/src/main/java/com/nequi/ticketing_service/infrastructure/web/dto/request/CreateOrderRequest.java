package com.nequi.ticketing_service.infrastructure.web.dto.request;

import java.util.List;

public record CreateOrderRequest(
        String userId,
        String eventId,
        double totalPrice,
        String currency,
        List<String> seatIds
) {}
