package com.nequi.inventory.infrastructure.config.initializer;

import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.IdempotencyEntity;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.TicketEntity;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DynamoDbInitializer {

    private final DynamoDbEnhancedAsyncClient enhancedClient;

    @PostConstruct
    public void init() {
        log.info("Iniciando validación y creación de tablas en DynamoDB...");
        try {
            CompletableFuture.allOf(
                    createTable(EventEntity.class, "Event"),
                    createTable(IdempotencyEntity.class, "Idempotency"),
                    createTicketTable()
            ).join();
            log.info("Finalizado: Todas las tablas están listas para operar.");
        } catch (Exception e) {
            log.error("Fallo crítico durante la inicialización de tablas: {}", e.getMessage());
        }
    }

    private <T> CompletableFuture<Void> createTable(Class<T> entityClass, String tableName) {
        return enhancedClient.table(tableName, TableSchema.fromBean(entityClass))
                .createTable(builder -> builder
                        .provisionedThroughput(ProvisionedThroughput.builder()
                                .readCapacityUnits(5L)
                                .writeCapacityUnits(5L)
                                .build()))
                .thenRun(() -> log.info("[SUCCESS] Tabla '{}' creada correctamente", tableName))
                .exceptionally(throwable -> {
                    if (throwable.getMessage().contains("Table already exists") ||
                            throwable.getCause() != null && throwable.getCause().getMessage().contains("Table already exists")) {
                        log.info("[SKIP] Tabla '{}' ya existe en DynamoDB", tableName);
                    } else {
                        log.warn("[ERROR] No se pudo crear la tabla '{}': {}", tableName, throwable.getMessage());
                    }
                    return null;
                });
    }

    private CompletableFuture<Void> createTicketTable() {
        var table = enhancedClient.table("Ticket", TableSchema.fromBean(TicketEntity.class));

        EnhancedGlobalSecondaryIndex statusIndex = EnhancedGlobalSecondaryIndex.builder()
                .indexName("status-index")
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(5L)
                        .build())
                .build();

        return table.createTable(builder -> builder
                        .globalSecondaryIndices(statusIndex)
                        .provisionedThroughput(ProvisionedThroughput.builder()
                                .readCapacityUnits(5L)
                                .writeCapacityUnits(5L)
                                .build()))
                .thenRun(() -> log.info("[SUCCESS] Tabla 'Ticket' creada correctamente con GSI: status-index"))
                .exceptionally(throwable -> {
                    if (throwable.getMessage().contains("Table already exists") ||
                            throwable.getCause() != null && throwable.getCause().getMessage().contains("Table already exists")) {
                        log.info("[SKIP] Tabla 'Ticket' ya existe en DynamoDB");
                    } else {
                        log.warn("[ERROR] No se pudo crear la tabla 'Ticket': {}", throwable.getMessage());
                    }
                    return null;
                });
    }
}