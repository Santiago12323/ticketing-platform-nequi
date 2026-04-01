package com.nequi.ticketing_service.infrastructure.web.dto.response;

import java.time.Instant;

public record OrderStatusResponse(
        String orderId,
        String status,
        Instant updatedAt
) {}