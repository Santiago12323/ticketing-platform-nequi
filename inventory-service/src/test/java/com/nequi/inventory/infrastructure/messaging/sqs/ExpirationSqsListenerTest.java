package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpirationSqsListenerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @InjectMocks
    private ExpirationSqsListener expirationSqsListener;

    private final String dlqUrl = "http://localhost:4566/000000000000/inventory-dlq";
    private final String orderIdUuid = java.util.UUID.randomUUID().toString();
    private final String validMessage = "{\"orderId\":\"" + orderIdUuid + "\", \"ticketIds\":[\"T1\", \"T2\"]}";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(expirationSqsListener, "dlqUrl", dlqUrl);
        ReflectionTestUtils.setField(expirationSqsListener, "auditEnabled", true);
    }

    @Test
    @DisplayName("Should process expiration successfully")
    void onMessageReceivedSuccess() throws JsonProcessingException {
        // GIVEN
        Map<String, Object> messageMap = Map.of(
                "orderId", orderIdUuid,
                "ticketIds", List.of("T1", "T2")
        );

        when(objectMapper.readValue(validMessage, Map.class)).thenReturn(messageMap);
        when(inventoryService.releaseReservedStock(any(OrderId.class), anySet()))
                .thenReturn(Mono.empty());

        // WHEN
        expirationSqsListener.onMessageReceived(validMessage);

        // THEN
        verify(inventoryService, times(1)).releaseReservedStock(eq(OrderId.of(orderIdUuid)), anySet());
        verify(sqsAsyncClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Should send to DLQ when message is malformed (missing fields)")
    void onMessageReceivedMalformedToDlq() throws JsonProcessingException {
        // GIVEN
        String malformedMessage = "{\"something\":\"else\"}";
        Map<String, Object> messageMap = Map.of("something", "else");

        when(objectMapper.readValue(malformedMessage, Map.class)).thenReturn(messageMap);
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        // WHEN
        expirationSqsListener.onMessageReceived(malformedMessage);

        // THEN
        verify(inventoryService, never()).releaseReservedStock(any(), any());
        verify(sqsAsyncClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Should send to DLQ when inventory service fails")
    void onMessageReceivedServiceErrorToDlq() throws JsonProcessingException {
        // GIVEN
        Map<String, Object> messageMap = Map.of(
                "orderId", orderIdUuid,
                "ticketIds", List.of("T1")
        );

        when(objectMapper.readValue(validMessage, Map.class)).thenReturn(messageMap);
        when(inventoryService.releaseReservedStock(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Database error")));

        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        // WHEN
        expirationSqsListener.onMessageReceived(validMessage);

        // THEN
        verify(sqsAsyncClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Should handle JSON parse error and send to DLQ")
    void onMessageReceivedJsonErrorToDlq() throws JsonProcessingException {
        // GIVEN
        String badJson = "invalid-json";
        when(objectMapper.readValue(badJson, Map.class)).thenThrow(new RuntimeException("Jackson error"));

        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        // WHEN
        expirationSqsListener.onMessageReceived(badJson);

        // THEN
        verify(sqsAsyncClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }
}