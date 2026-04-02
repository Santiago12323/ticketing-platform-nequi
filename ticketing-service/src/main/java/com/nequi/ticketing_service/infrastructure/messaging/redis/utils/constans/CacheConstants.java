package com.nequi.ticketing_service.infrastructure.messaging.redis.utils.constans;

public final class CacheConstants {
    public static final String ORDER_PREFIX = "order:cache:";
    public static final int ORDER_CACHE_TTL_MINUTES = 5;

    public static final String ORDER_CONSUMER_GROUP = "order-group";
    public static final String ORDER_CONSUMER_NAME = "order";

    public static final int STREAM_LIMIT_RATE = 100;
    public static final int STREAM_PARALLELISM = 10;

    private CacheConstants() {}
}