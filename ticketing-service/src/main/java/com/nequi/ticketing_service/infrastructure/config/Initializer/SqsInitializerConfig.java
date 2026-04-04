package com.nequi.ticketing_service.infrastructure.config.Initializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;

import java.util.Map;

@Configuration
public class SqsInitializerConfig {

    @Bean
    public CommandLineRunner initializeQueues(SqsAsyncClient sqsClient) {
        return args -> {
            createFifoQueue(sqsClient, "inventory-request-queue.fifo");
            createFifoQueue(sqsClient, "inventory-response-queue.fifo");
        };
    }

    private void createFifoQueue(SqsAsyncClient sqsClient, String queueName) {
        sqsClient.createQueue(b -> b
                        .queueName(queueName)
                        .attributes(Map.of(
                                QueueAttributeName.FIFO_QUEUE, "true",
                                QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true"
                        ))
                )
                .handle((res, ex) -> {
                    if (ex != null) {
                        if (ex.getMessage().contains("QueueNameExistsException") ||
                                ex.getCause() instanceof QueueNameExistsException) {
                            System.out.println("La cola '" + queueName + "' ya existe con otros atributos. Usando la existente.");
                        } else {
                            System.err.println("Error real al crear cola: " + ex.getMessage());
                        }
                    } else {
                        System.out.println("Cola creada exitosamente: " + queueName);
                    }
                    return null;
                });
    }
}