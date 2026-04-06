package com.nequi.ticketing_service.application.infrastruture.messaging.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.in.ProcessInventoryResponseUseCase;
import com.nequi.ticketing_service.domain.statemachine.OrderEvent;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.SqsInventoryResponseListener;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.enums.Type;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsInventoryResponseListenerTest {

    @Mock
    private ProcessInventoryResponseUseCase processUseCase;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @InjectMocks
    private SqsInventoryResponseListener listener;

    private static final String DLQ_URL = "http://localhost/inventory-dlq";
    private static final String ORDER_ID = UUID.randomUUID().toString();
    private static final String RAW_MESSAGE = "{\"orderId\":\"" + ORDER_ID + "\"}";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "inventoryResponseDlqUrl", DLQ_URL);
        ReflectionTestUtils.setField(listener, "auditEnabled", true);
    }

    @ParameterizedTest
    @DisplayName("AAA - Switch Coverage: Should map correct events based on Type and Success")
    @CsvSource({
            "RESERVE, true, VALIDATION_SUCCESS",
            "RESERVE, false, VALIDATION_FAILED",
            "PAYMENT, true, CONFIRM_PAYMENT",
            "PAYMENT, false, FAIL_PAYMENT"
    })
    void onMessage_SuccessFlows(String typeStr, boolean success, String expectedEventStr) throws JsonProcessingException {
        // Arrange
        Type type = Type.valueOf(typeStr);
        OrderEvent expectedEvent = OrderEvent.valueOf(expectedEventStr);
        InventoryResponse response = success ?
                InventoryResponse.success(ORDER_ID, type) :
                InventoryResponse.failure(ORDER_ID, List.of("TKT-1"), type);

        when(objectMapper.readValue(RAW_MESSAGE, InventoryResponse.class)).thenReturn(response);
        when(processUseCase.execute(any(OrderId.class), eq(expectedEvent))).thenReturn(Mono.empty());

        // Act
        listener.onMessage(RAW_MESSAGE);

        // Assert
        verify(processUseCase, timeout(1000)).execute(argThat(id -> id.value().equals(ORDER_ID)), eq(expectedEvent));
    }

    @Test
    @DisplayName("AAA - Error Flow: Should send to DLQ when JSON is invalid")
    void onMessage_JsonError_SendsToDlq() throws JsonProcessingException {
        // Arrange
        when(objectMapper.readValue(anyString(), eq(InventoryResponse.class)))
                .thenThrow(new RuntimeException("JSON Parse Error"));

        when(sqsAsyncClient.sendMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        // Act
        listener.onMessage("invalid-json");

        // Assert
        verify(sqsAsyncClient, timeout(1000)).sendMessage(any(Consumer.class));
    }

    @Test
    @DisplayName("AAA - DLQ Fatal: Should log error when SQS fails")
    void onMessage_DlqFatalError() throws JsonProcessingException {
        // Arrange
        when(objectMapper.readValue(anyString(), eq(InventoryResponse.class))).thenThrow(new RuntimeException("Error"));
        CompletableFuture<SendMessageResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("SQS Down"));

        when(sqsAsyncClient.sendMessage(any(Consumer.class))).thenReturn(failedFuture);

        // Act
        listener.onMessage(RAW_MESSAGE);

        // Assert
        verify(sqsAsyncClient, timeout(1000)).sendMessage(any(Consumer.class));
    }
}