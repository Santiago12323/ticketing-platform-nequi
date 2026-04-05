package com.nequi.inventory.infrastructure.messaging.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.inventory.domain.port.in.IdempotencyService;
import com.nequi.inventory.domain.port.in.InventoryService;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.request.InventoryCheckRequest;
import com.nequi.inventory.infrastructure.messaging.sqs.enums.OrderEvent;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsListenerImplTest {

    @Mock private InventoryService inventoryService;
    @Mock private ObjectMapper objectMapper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private SqsAsyncClient sqsAsyncClient;

    @InjectMocks
    private SqsListenerImpl sqsListener;

    private final String dlqUrl = "http://localhost:4566/inventory-dlq";
    private final String message = "{\"orderId\":\"123\"}";
    private OrderId orderId;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        orderId = OrderId.of(UUID.randomUUID().toString());
        eventId = EventId.of(UUID.randomUUID().toString());
        ReflectionTestUtils.setField(sqsListener, "inventoryRequestDlqUrl", dlqUrl);
        ReflectionTestUtils.setField(sqsListener, "auditEnabled", true);
    }

    @Test
    @DisplayName("Branch: START_PROCESS - New Request (Full Coverage)")
    void testHandleReserveFlowNew() throws JsonProcessingException {
        InventoryCheckRequest request = InventoryCheckRequest.of(orderId, eventId, List.of(new TicketId("T1")));

        when(objectMapper.readValue(anyString(), eq(InventoryCheckRequest.class))).thenReturn(request);
        when(idempotencyService.tryProcess(any())).thenReturn(Mono.just(true));
        when(inventoryService.reserve(any(), any(), any())).thenReturn(Mono.empty());

        sqsListener.onMessage(message).join();

        verify(inventoryService).reserve(any(), any(), any());
    }

    @Test
    @DisplayName("Branch: START_PROCESS - Duplicate Request (Idempotency Coverage)")
    void testHandleReserveFlowDuplicate() throws JsonProcessingException {
        InventoryCheckRequest request = InventoryCheckRequest.of(orderId, eventId, List.of(new TicketId("T1")));

        when(objectMapper.readValue(anyString(), eq(InventoryCheckRequest.class))).thenReturn(request);
        when(idempotencyService.tryProcess(any())).thenReturn(Mono.just(false));

        sqsListener.onMessage(message).join();

        verify(inventoryService, never()).reserve(any(), any(), any());
    }

    @Test
    @DisplayName("Branch: CONFIRM_PAYMENT (Coverage)")
    void testHandlePaymentFlow() throws JsonProcessingException {
        InventoryCheckRequest request = InventoryCheckRequest.forPayment(orderId, "PAY-1");

        when(objectMapper.readValue(anyString(), eq(InventoryCheckRequest.class))).thenReturn(request);
        when(inventoryService.confirm(any(), any(), any())).thenReturn(Mono.empty());

        sqsListener.onMessage(message).join();

        verify(inventoryService).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("Branch: Default Switch (Unsupported Event Coverage)")
    void testUnsupportedEvent() throws JsonProcessingException {
        InventoryCheckRequest request = new InventoryCheckRequest(
                orderId,
                eventId,
                List.of(),
                OrderEvent.CANCEL,
                null,
                java.time.Instant.now()
        );

        when(objectMapper.readValue(anyString(), eq(InventoryCheckRequest.class))).thenReturn(request);

        sqsListener.onMessage(message).join();

        verify(inventoryService, never()).reserve(any(), any(), any());
        verify(inventoryService, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("Flow: Error & DLQ (onErrorResume Coverage)")
    void testErrorAndSendToDlq() throws JsonProcessingException {
        when(objectMapper.readValue(anyString(), eq(InventoryCheckRequest.class)))
                .thenThrow(new RuntimeException("JSON Error"));

        when(sqsAsyncClient.sendMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        sqsListener.onMessage(message).join();

        verify(sqsAsyncClient).sendMessage(any(Consumer.class));
    }

    @Test
    @DisplayName("Flow: DLQ Fatal Error Coverage")
    void testDlqFatalError() throws JsonProcessingException {
        when(objectMapper.readValue(anyString(), eq(InventoryCheckRequest.class)))
                .thenThrow(new RuntimeException("Initial Error"));

        CompletableFuture<SendMessageResponse> fatalError = new CompletableFuture<>();
        fatalError.completeExceptionally(new RuntimeException("SQS Down"));

        when(sqsAsyncClient.sendMessage(any(Consumer.class))).thenReturn(fatalError);

        assertThrows(Exception.class, () -> {
            sqsListener.onMessage(message).join();
        });

        verify(sqsAsyncClient).sendMessage(any(Consumer.class));
    }
}