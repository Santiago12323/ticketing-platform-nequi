package com.nequi.ticketing_service.infrastructure.config;

import com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity.OrderEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbAsyncClient dynamoDbAsyncClient() {
        return DynamoDbAsyncClient.builder()
                .region(Region.US_EAST_1) // O la que uses
                .build();
    }

    @Bean
    public DynamoDbEnhancedAsyncClient enhancedAsyncClient(DynamoDbAsyncClient asyncClient) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(asyncClient)
                .build();
    }

    @Bean
    public DynamoDbAsyncTable<OrderEntity> orderTable(DynamoDbEnhancedAsyncClient enhancedClient) {
        return enhancedClient.table("Orders", TableSchema.fromBean(OrderEntity.class));
    }
}