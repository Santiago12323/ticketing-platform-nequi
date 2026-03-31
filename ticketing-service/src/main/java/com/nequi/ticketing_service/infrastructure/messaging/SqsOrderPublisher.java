package com.nequi.ticketing_service.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.out.OrderPublisher;
import com.nequi.ticketing_service.domain.valueobject.EventId;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.dto.InventoryCheckMessage;
import com.nequi.ticketing_service.infrastructure.messaging.dto.OrderCreatedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SqsOrderPublisher implements OrderPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.endpoint}")
    private String queueUrl;

    @Override
    public Mono<Boolean> publishInventoryCheck(OrderId orderId, EventId eventId, List<String> seatIds) {
        return Mono.fromFuture(() -> {
            try {
                InventoryCheckMessage payload = new InventoryCheckMessage(orderId.value(), eventId.value(), seatIds);
                String json = objectMapper.writeValueAsString(payload);

                SendMessageRequest request = SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(json)
                        .build();

                return sqsClient.sendMessage(request);
            } catch (Exception e) {
                throw new RuntimeException("Failed to publish inventory check event", e);
            }
        }).map(response -> response.sdkHttpResponse().isSuccessful());
    }

    @Override
    public Mono<Void> publishOrderCreated(OrderId orderId) {
        return Mono.fromFuture(() -> {
            try {
                OrderCreatedMessage payload = new OrderCreatedMessage(orderId.value());
                String json = objectMapper.writeValueAsString(payload);

                SendMessageRequest request = SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(json)
                        .build();

                return sqsClient.sendMessage(request);
            } catch (Exception e) {
                throw new RuntimeException("Failed to publish order created event", e);
            }
        }).then();
    }
}
