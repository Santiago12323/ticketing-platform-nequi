package com.nequi.ticketing_service.infrastructure.persistence.dynamo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class OrderEntity {
    private String id;
    private String eventId;
    private String userId;
    private String status;
    private Double amount;
    private String currency;
    private String createdAt;
    private String updatedAt;
    private List<String> seatIds;

    @DynamoDbPartitionKey
    public String getId() { return id; }
}