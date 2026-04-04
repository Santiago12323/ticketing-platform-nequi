package com.nequi.ticketing_service.infrastructure.config.Initializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Configuration
public class SqsInitializerConfig {

    private static final String REGION = "us-west-2";
    private static final String ACCOUNT = "000000000000"; // En LocalStack es fija

    @Bean
    public CommandLineRunner initializeQueues(SqsAsyncClient sqsClient) {
        return args -> {
            System.out.println("Iniciando validación de infraestructura SQS...");

            initQueueFlow(sqsClient, "inventory-request").join();
            initQueueFlow(sqsClient, "inventory-response").join();

            sqsClient.createQueue(b -> b.queueName("order-ttl-queue")).join();
            sqsClient.createQueue(b -> b.queueName("inventory-ttl-queue")).join();

            System.out.println("Infraestructura SQS lista.");
        };
    }

    private CompletableFuture<Void> initQueueFlow(SqsAsyncClient sqsClient, String baseName) {
        String dlqName = baseName + "-dlq.fifo";
        String queueName = baseName + "-queue.fifo";

        return sqsClient.createQueue(b -> b.queueName(dlqName)
                        .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true")))
                .thenCompose(res -> {
                    String dlqArn = String.format("arn:aws:sqs:%s:%s:%s", REGION, ACCOUNT, dlqName);
                    String redrivePolicy = String.format("{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"3\"}", dlqArn);

                    return sqsClient.createQueue(b -> b.queueName(queueName)
                            .attributes(Map.of(
                                    QueueAttributeName.FIFO_QUEUE, "true",
                                    QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true",
                                    QueueAttributeName.REDRIVE_POLICY, redrivePolicy
                            )));
                })
                .thenAccept(res -> System.out.println("Finalizada creación de: " + queueName))
                .exceptionally(ex -> {
                    System.err.println("Error en flujo " + baseName + ": " + ex.getMessage());
                    return null;
                });
    }
}