package com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans;

import org.springframework.stereotype.Component;

@Component
public class CacheKeyGenerator {
    private static final String ORDER_PREFIX = "order:cache:";

    public String generateOrderKey(String id) {
        return ORDER_PREFIX + id;
    }
}