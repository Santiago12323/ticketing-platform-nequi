package com.nequi.inventory.infrastructure.persistence.dynamo.entity;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDBTable(tableName = "Events")
public class EventEntity {

    @DynamoDBHashKey
    private String eventId;

    private String name;
    private int capacity;
    private int availableSeats;
    private int reservedSeats;
    private int soldSeats;

    @DynamoDBVersionAttribute
    private Long version;
}
