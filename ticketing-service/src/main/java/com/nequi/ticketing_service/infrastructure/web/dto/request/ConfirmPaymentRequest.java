package com.nequi.ticketing_service.infrastructure.web.dto.request;

import java.util.List;

public record ConfirmPaymentRequest(
        String userId,
        String eventId,
        Double amount,
        String currency,
        List<String> seatIds,
        String orderId
) {}