package com.nequi.ticketing_service.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.in.ProcessInventoryResponseUseCase;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsInventoryResponseListener {

    private final ProcessInventoryResponseUseCase processUseCase;
    private final ObjectMapper objectMapper;

    @Value("${ticketing.statemachine.audit-enabled}")
    private boolean auditEnabled;

    @SqsListener("${aws.sqs.inventory-response-queue}")
    public void onMessage(String message) {
        Mono.fromCallable(() -> objectMapper.readValue(message, InventoryResponse.class))
                .flatMap(response -> {
                    logAudit("Received inventory response for Order: {} - Success: {}",
                            response.orderId(), response);

                    return processUseCase.execute(response.orderId(), response.success());
                })

                .doOnError(e -> logError("Error processing inventory SQS message", e))
                .subscribe();
    }

    private void logAudit(String format, Object... args) {
        if (auditEnabled) log.info(format, args);
    }

    private void logError(String msg, Throwable e) {
        if (auditEnabled) log.error("{}: {}", msg, e.getMessage());
    }
}