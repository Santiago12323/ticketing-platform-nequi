package com.nequi.ticketing_service.infrastructure.config.Initializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

@Configuration
public class RedisInitializerConfig {

    @Bean
    public CommandLineRunner initializeRedisGroup(ReactiveRedisTemplate<String, String> redisTemplate) {
        return args -> {
            redisTemplate.opsForStream()
                    .createGroup("orders", "order-group")
                    .doOnError(e -> {
                        if (!e.getMessage().contains("BUSYGROUP")) {
                            System.err.println("Error creando grupo en Redis: " + e.getMessage());
                        }
                    })
                    .doOnSuccess(v -> System.out.println("🚀 Grupo 'order-group' creado en stream 'orders'."))
                    .subscribe();
        };
    }
}
