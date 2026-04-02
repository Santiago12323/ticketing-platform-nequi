package com.nequi.ticketing_service.infrastructure.config.Initializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class SqsInitializerConfig {

    @Bean
    public CommandLineRunner initializeQueues(SqsAsyncClient sqsClient) {
        return args -> {
            sqsClient.createQueue(b -> b.queueName("inventory-request-queue"))
                    .handle((res, ex) -> {
                        if (ex != null && !ex.getMessage().contains("QueueAlreadyExists")) {
                            System.err.println("Error creando request queue: " + ex.getMessage());
                        } else {
                            System.out.println("🚀 Cola 'inventory-request-queue' lista.");
                        }
                        return null;
                    });

            sqsClient.createQueue(b -> b.queueName("inventory-response-queue"))
                    .handle((res, ex) -> {
                        if (ex != null && !ex.getMessage().contains("QueueAlreadyExists")) {
                            System.err.println("Error creando response queue: " + ex.getMessage());
                        } else {
                            System.out.println("🚀 Cola 'inventory-response-queue' lista.");
                        }
                        return null;
                    });
        };
    }
}
