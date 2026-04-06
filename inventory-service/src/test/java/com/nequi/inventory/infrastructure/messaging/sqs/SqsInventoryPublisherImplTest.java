package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import com.nequi.inventory.infrastructure.messaging.sqs.enums.Type;
import com.nequi.inventory.infrastructure.messaging.sqs.utils.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsInventoryPublisherImplTest {

    @Mock private SqsAsyncClient sqsClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private MessagingProperties properties;

    @InjectMocks
    private SqsInventoryPublisherImpl publisher;

    private InventoryResponse response;
    private MessagingProperties.Sqs sqsProps;
    private MessagingProperties.Retry retryProps;
    private MessagingProperties.Audit auditProps;

    @BeforeEach
    void setUp() {
        response = InventoryResponse.success(UUID.randomUUID().toString(), Type.PAYMENT);

        sqsProps = mock(MessagingProperties.Sqs.class);
        retryProps = mock(MessagingProperties.Retry.class);
        auditProps = mock(MessagingProperties.Audit.class);

        lenient().when(properties.getSqs()).thenReturn(sqsProps);
        lenient().when(properties.getRetry()).thenReturn(retryProps);
        lenient().when(properties.getAudit()).thenReturn(auditProps);

        lenient().when(sqsProps.getResponseQueueUrl()).thenReturn("http://localhost:4566/response-queue");
        lenient().when(retryProps.getMaxAttempts()).thenReturn(1);
        lenient().when(retryProps.getBackoffMillis()).thenReturn(10L);
        lenient().when(auditProps.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("Should fail serialization and exhaust retries")
    void publishSerializationError() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Jackson Fail"));

        StepVerifier.create(publisher.publishInventoryResponse(response))
                .expectErrorSatisfies(throwable -> {
                    assertTrue(Exceptions.isRetryExhausted(throwable));
                    assertTrue(throwable.getCause().getMessage().contains("Error serializing"));
                })
                .verify();
    }

    @Test
    @DisplayName("Should retry and fail when SQS client is down")
    void publishSqsFailureWithRetry() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        CompletableFuture<SendMessageResponse> fatalFuture = new CompletableFuture<>();
        fatalFuture.completeExceptionally(new RuntimeException("SQS Down"));

        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(fatalFuture);

        StepVerifier.create(publisher.publishInventoryResponse(response))
                .expectErrorSatisfies(throwable -> {
                    assertTrue(Exceptions.isRetryExhausted(throwable));
                })
                .verify();

        verify(sqsClient, times(2)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Should publish successfully")
    void publishSuccess() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("id-123").build()
                ));

        StepVerifier.create(publisher.publishInventoryResponse(response))
                .verifyComplete();

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }
}