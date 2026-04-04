package com.nequi.inventory.infrastructure.persistence.dynamo.entity;

import com.nequi.inventory.domain.statemachine.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class TicketEntity {

    private String eventId;
    private String ticketId;
    private TicketStatus status;
    private String userId;
    private String orderId;
    private String expiresAt;
    private Long version;
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("eventId")
    public String getEventId() { return eventId; }

    @DynamoDbSortKey
    @DynamoDbAttribute("ticketId")
    public String getTicketId() { return ticketId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    @DynamoDbAttribute("status")
    public TicketStatus getStatus() { return status; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }

}