package com.nequi.ticketing_service.application.infrastruture.messaging.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.domain.valueobject.TicketId;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.SqsOrderPublisher;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.request.InventoryCheckRequest;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.utils.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsOrderPublisherTest {

    @Mock
    private SqsAsyncClient sqsClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MessagingProperties properties;

    @InjectMocks
    private SqsOrderPublisher publisher;

    private OrderId orderId;
    private EventId eventId;
    private List<TicketId> ticketIds;
    private String queueUrl = "http://localhost/queue";

    @BeforeEach
    void setUp() {
        orderId = new OrderId(UUID.randomUUID().toString());
        eventId = new EventId(UUID.randomUUID().toString());
        ticketIds = List.of(new TicketId(UUID.randomUUID().toString()));

        MessagingProperties.Sqs sqs = mock(MessagingProperties.Sqs.class);
        MessagingProperties.Retry retry = mock(MessagingProperties.Retry.class);
        MessagingProperties.Audit audit = mock(MessagingProperties.Audit.class);

        lenient().when(properties.getSqs()).thenReturn(sqs);
        lenient().when(properties.getRetry()).thenReturn(retry);
        lenient().when(properties.getAudit()).thenReturn(audit);

        lenient().when(sqs.getInventoryQueueUrl()).thenReturn(queueUrl);
        lenient().when(retry.getMaxAttempts()).thenReturn(1);
        lenient().when(retry.getBackoffMillis()).thenReturn(10L);
        lenient().when(audit.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("AAA - Success: Should publish inventory check requested")
    void publishInventoryCheck_Success() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any(InventoryCheckRequest.class))).thenReturn("{}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("123").build()));

        // Act & Assert
        StepVerifier.create(publisher.publishInventoryCheck(orderId, eventId, ticketIds))
                .verifyComplete();

        verify(sqsClient).sendMessage(argThat((SendMessageRequest req) ->
                req.messageAttributes().get("eventType").stringValue().equals("InventoryCheckRequested")
        ));
    }

    @Test
    @DisplayName("AAA - Success: Should publish payment confirmed")
    void publishPaymentConfirmed_Success() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any(InventoryCheckRequest.class))).thenReturn("{}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("456").build()));

        // Act & Assert
        StepVerifier.create(publisher.publishPaymentConfirmed(orderId, "pay-123", eventId, ticketIds))
                .verifyComplete();

        verify(sqsClient).sendMessage(argThat((SendMessageRequest req) ->
                req.messageAttributes().get("eventType").stringValue().equals("PaymentConfirmed")
        ));
    }

    @Test
    @DisplayName("AAA - Error: Should throw RuntimeException when serialization fails")
    void executePublish_SerializationError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Jackson Error"));

        // Act & Assert
        StepVerifier.create(publisher.publishInventoryCheck(orderId, eventId, ticketIds))
                .expectErrorMatches(throwable -> {
                    Throwable cause = (throwable.getCause() != null) ? throwable.getCause() : throwable;
                    return cause instanceof RuntimeException &&
                            cause.getMessage().equals("Error serializing request");
                })
                .verify();
    }

    @Test
    @DisplayName("AAA - Retry: Should retry and then fail when SQS is down")
    void executePublish_WithRetry() throws JsonProcessingException {
        // Arrange
        when(properties.getRetry().getMaxAttempts()).thenReturn(2);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        CompletableFuture<SendMessageResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("SQS Down"));

        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(failedFuture);

        // Act & Assert
        StepVerifier.create(publisher.publishInventoryCheck(orderId, eventId, ticketIds))
                .expectError()
                .verify(Duration.ofSeconds(2));
        verify(sqsClient, times(3)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("AAA - Branch: Should not log audit when disabled")
    void logAudit_Disabled() throws JsonProcessingException {
        // Arrange
        when(properties.getAudit().isEnabled()).thenReturn(false);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        // Act & Assert
        StepVerifier.create(publisher.publishInventoryCheck(orderId, eventId, ticketIds))
                .verifyComplete();
    }
}