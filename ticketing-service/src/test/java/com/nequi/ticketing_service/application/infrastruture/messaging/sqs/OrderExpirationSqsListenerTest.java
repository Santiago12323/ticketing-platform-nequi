package com.nequi.ticketing_service.application.infrastruture.messaging.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.in.OrderUseCase;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.OrderExpirationSqsListener;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSqsListenerTest {

    @Mock
    private OrderUseCase orderService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @InjectMocks
    private OrderExpirationSqsListener listener;

    private static final String DLQ_URL = "http://localhost/dlq";
    private static final String VALID_UUID = UUID.randomUUID().toString();
    private static final String MESSAGE_BODY = "{\"orderId\":\"" + VALID_UUID + "\"}";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "dlqUrl", DLQ_URL);
        ReflectionTestUtils.setField(listener, "auditEnabled", true);
    }

    @Test
    @DisplayName("AAA - Success Flow: Should process expiration when message is valid")
    void onMessageReceived_Success() throws JsonProcessingException {
        // Arrange
        Map<String, String> messageMap = Map.of("orderId", VALID_UUID);
        when(objectMapper.readValue(MESSAGE_BODY, Map.class)).thenReturn(messageMap);
        when(orderService.expireOrder(any(OrderId.class))).thenReturn(Mono.empty());

        // Act
        listener.onMessageReceived(MESSAGE_BODY);

        // Assert
        verify(orderService, timeout(1000)).expireOrder(argThat(id -> id.value().equals(VALID_UUID)));
        verify(sqsAsyncClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("AAA - Exception Flow: Should send to DLQ when mapper fails")
    void onMessageReceived_MapperError_SendsToDlq() throws JsonProcessingException {
        // Arrange
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        // Act
        listener.onMessageReceived("invalid-json");

        // Assert
        verify(sqsAsyncClient, timeout(1000)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("AAA - Logical Flow: Should send to DLQ when orderId is missing")
    void onMessageReceived_MissingId_SendsToDlq() throws JsonProcessingException {
        // Arrange
        Map<String, String> incompleteMap = Map.of("otherField", "data");
        when(objectMapper.readValue(MESSAGE_BODY, Map.class)).thenReturn(incompleteMap);

        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        // Act
        listener.onMessageReceived(MESSAGE_BODY);

        // Assert
        verify(sqsAsyncClient, timeout(1000)).sendMessage(argThat((SendMessageRequest req) ->
                req.queueUrl().equals(DLQ_URL) &&
                        req.messageBody().equals(MESSAGE_BODY)
        ));

        verify(orderService, never()).expireOrder(any());
    }

    @Test
    @DisplayName("AAA - Infrastructure Flow: Should handle DLQ failure gracefully")
    void sendToDlq_SqsError_LogsException() throws JsonProcessingException {
        // Arrange
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(new RuntimeException("Force DLQ"));

        CompletableFuture<SendMessageResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("SQS Network Fail"));

        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class))).thenReturn(failedFuture);

        // Act
        listener.onMessageReceived(MESSAGE_BODY);

        // Assert
        verify(sqsAsyncClient, timeout(1000)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("AAA - Coverage Flow: Should work when audit is disabled")
    void logAudit_Disabled_CoversBranch() throws JsonProcessingException {
        // Arrange
        ReflectionTestUtils.setField(listener, "auditEnabled", false);
        Map<String, String> messageMap = Map.of("orderId", VALID_UUID);
        when(objectMapper.readValue(MESSAGE_BODY, Map.class)).thenReturn(messageMap);
        when(orderService.expireOrder(any(OrderId.class))).thenReturn(Mono.empty());

        // Act
        listener.onMessageReceived(MESSAGE_BODY);

        // Assert
        verify(orderService, timeout(1000)).expireOrder(any());
    }
}