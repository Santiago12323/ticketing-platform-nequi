package com.nequi.ticketing_service.infrastructure.messaging.dto;

public record OrderCreatedMessage(
        String orderId
) {}
