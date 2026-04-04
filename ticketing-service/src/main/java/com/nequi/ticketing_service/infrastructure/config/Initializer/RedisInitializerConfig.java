package com.nequi.ticketing_service.infrastructure.config.Initializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

@Configuration
public class RedisInitializerConfig {

    @Bean
    public CommandLineRunner initializeRedisGroup(ReactiveRedisTemplate<String, String> redisTemplate) {
        return args -> {
            String streamKey = "orders";
            String groupName = "order-group";

            redisTemplate.opsForStream()
                    .createGroup(streamKey, groupName)
                    .onErrorResume(e -> {
                        if (e.getMessage().contains("BUSYGROUP")) {
                            System.out.println("El grupo '" + groupName + "' ya existía. Saltando creación.");
                            return Mono.empty();
                        } else if (e.getMessage().contains("no such key")) {

                            return redisTemplate.opsForStream()
                                    .createGroup(streamKey, ReadOffset.latest(), groupName)
                                    .doOnSuccess(v -> System.out.println(" Stream 'orders' y grupo 'order-group' creados."));
                        }
                        return Mono.error(e);
                    })
                    .doOnSuccess(v -> {
                        if (v != null) System.out.println("Configuración de Redis Stream finalizada.");
                    })
                    .subscribe(
                            null,
                            error -> System.err.println("Error crítico inicializando Redis: " + error.getMessage())
                    );
        };
    }
}
