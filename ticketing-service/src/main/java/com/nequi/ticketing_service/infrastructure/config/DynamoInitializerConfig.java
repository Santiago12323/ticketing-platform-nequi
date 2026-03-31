package com.nequi.ticketing_service.infrastructure.config;

import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;

@Configuration
public class DynamoInitializerConfig {

    @Bean
    public CommandLineRunner initializeTable(DynamoDbAsyncTable<OrderEntity> orderTable) {
        return args -> {
            orderTable.createTable()
                    .handle((res, ex) -> {
                        if (ex != null) {
                            if (!ex.getMessage().contains("Table already exists")) {
                                System.err.println("Error al crear tabla: " + ex.getMessage());
                            }
                        } else {
                            System.out.println("🚀 Tabla 'Orders' creada exitosamente en DynamoDB Local.");
                        }
                        return null;
                    });
        };
    }
}