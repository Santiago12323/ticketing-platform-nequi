package com.nequi.inventory.infrastructure.messaging.sns;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.valueobject.EventId;
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
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnsExpirationPublisherImplTest {

    @Mock
    private SnsAsyncClient snsAsyncClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SnsExpirationPublisherImpl snsExpirationPublisher;

    private final String TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:order-expiration-topic";
    private OrderId orderId;
    private EventId eventId;
    private Set<TicketId> ticketIds;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(snsExpirationPublisher, "topicArn", TOPIC_ARN);

        orderId = OrderId.of(UUID.randomUUID().toString());
        eventId = EventId.of(UUID.randomUUID().toString());
        ticketIds = Set.of(TicketId.of("T-1"), TicketId.of("T-2"));
    }

    @Test
    @DisplayName("Should publish event to SNS successfully")
    void publishExpirationEvent_Success() throws JsonProcessingException {
        // Arrange
        String payload = "{\"orderId\":\"...\"}";
        when(objectMapper.writeValueAsString(anyMap())).thenReturn(payload);

        PublishResponse mockResponse = PublishResponse.builder().messageId("msg-123").build();
        when(snsAsyncClient.publish(any(PublishRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // Act & Assert
        StepVerifier.create(snsExpirationPublisher.publishExpirationEvent(orderId, eventId, ticketIds))
                .verifyComplete();

        verify(snsAsyncClient).publish(argThat((PublishRequest request) ->
                request.topicArn().equals(TOPIC_ARN) &&
                        request.message().equals(payload) &&
                        request.messageAttributes().containsKey("type") &&
                        request.messageAttributes().get("type").stringValue().equals("ORDER_EXPIRATION")
        ));
    }

    @Test
    @DisplayName("Should fail when serialization fails")
    void publishExpirationEvent_SerializationError() throws JsonProcessingException {

        when(objectMapper.writeValueAsString(anyMap()))
                .thenThrow(new RuntimeException("Jackson error"));

        // Act & Assert
        StepVerifier.create(snsExpirationPublisher.publishExpirationEvent(orderId, eventId, ticketIds))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Error serializing SNS payload"))
                .verify();
    }

    @Test
    @DisplayName("Should handle SNS service failure by propagating the error")
    void publishExpirationEvent_SnsFailure() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        CompletableFuture<PublishResponse> futureError = new CompletableFuture<>();
        futureError.completeExceptionally(new RuntimeException("SNS Service Unavailable"));

        when(snsAsyncClient.publish(any(PublishRequest.class))).thenReturn(futureError);

        // Act & Assert
        StepVerifier.create(snsExpirationPublisher.publishExpirationEvent(orderId, eventId, ticketIds))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("SNS Service Unavailable"))
                .verify();
    }
}