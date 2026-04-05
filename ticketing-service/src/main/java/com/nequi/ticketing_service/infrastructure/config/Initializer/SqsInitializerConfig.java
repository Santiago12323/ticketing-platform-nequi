package com.nequi.ticketing_service.infrastructure.config.Initializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Configuration
public class SqsInitializerConfig {

    private static final String REGION = "us-west-2";
    private static final String ACCOUNT = "000000000000";

    @Bean
    public CommandLineRunner initializeQueues(SqsAsyncClient sqsClient) {
        return args -> {
            System.out.println("Iniciando validación de infraestructura SQS...");

            initQueueFlow(sqsClient, "inventory-request").join();
            initQueueFlow(sqsClient, "inventory-response").join();

            ensureQueueExists(sqsClient, "order-ttl-queue", Map.of(
                    QueueAttributeName.DELAY_SECONDS, "600"
            )).join();

            ensureQueueExists(sqsClient, "inventory-ttl-queue", Map.of(
                    QueueAttributeName.DELAY_SECONDS, "600"
            )).join();

            System.out.println("✅ Infraestructura SQS validada y lista.");
        };
    }

    private CompletableFuture<Void> initQueueFlow(SqsAsyncClient sqsClient, String baseName) {
        String dlqName = baseName + "-dlq.fifo";
        String queueName = baseName + "-queue.fifo";
        String dlqArn = String.format("arn:aws:sqs:%s:%s:%s", REGION, ACCOUNT, dlqName);

        return ensureQueueExists(sqsClient, dlqName, Map.of(
                QueueAttributeName.FIFO_QUEUE, "true"
        )).thenCompose(v -> {
            String redrivePolicy = String.format("{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"3\"}", dlqArn);

            return ensureQueueExists(sqsClient, queueName, Map.of(
                    QueueAttributeName.FIFO_QUEUE, "true",
                    QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true",
                    QueueAttributeName.REDRIVE_POLICY, redrivePolicy
            ));
        }).thenAccept(res -> System.out.println("✔ Validada: " + queueName));
    }

    private CompletableFuture<Void> ensureQueueExists(SqsAsyncClient sqsClient, String name, Map<QueueAttributeName, String> attributes) {
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build())
                .thenAccept(res -> System.out.println(" La cola ya existe: " + name))
                .handle((res, ex) -> {
                    if (ex != null) {
                        if (ex.getCause() instanceof QueueDoesNotExistException || ex instanceof QueueDoesNotExistException) {
                            System.out.println("➕ Creando cola: " + name);
                            return sqsClient.createQueue(CreateQueueRequest.builder()
                                    .queueName(name)
                                    .attributes(attributes)
                                    .build()).thenRun(() -> System.out.println("Creada con éxito: " + name));
                        }
                        System.err.println(" Error inesperado validando " + name + ": " + ex.getMessage());
                    }
                    return CompletableFuture.completedFuture(null);
                }).thenCompose(f -> (CompletableFuture<Void>) f);
    }
}