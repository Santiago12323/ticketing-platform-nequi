package com.nequi.inventory.infrastructure.persistence.dynamo.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;

@DynamoDbBean
public class EventEntity {

    private String eventId;
    private String name;
    private int totalCapacity;
    private String location;
    private EventStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    @DynamoDbPartitionKey
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}