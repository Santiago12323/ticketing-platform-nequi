package com.nequi.ticketing_service.infrastructure.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.port.in.ProcessInventoryResponseUseCase;
import com.nequi.ticketing_service.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.annotation.PostConstruct;
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

    @SqsListener("${spring.cloud.aws.sqs.inventory-response-queue}")
    public void onMessage(String message) {
        log.info("RAW LLEGÓ: {}", message);

        if (auditEnabled) {
            log.info("[SQS AUDIT] Raw message received from inventory-response-queue: {}", message);
        }

        Mono.defer(() -> process(message))
                .doOnError(e -> log.error("[SQS ERROR] Failed to process inventory response: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> process(String message) {
        return Mono.fromCallable(() -> objectMapper.readValue(message, InventoryResponse.class))
                .doOnNext(response -> {
                    if (auditEnabled) {
                        log.info("[SQS AUDIT] Processing InventoryResponse: OrderId={}, Success={}, FailedTickets={}",
                                response.orderId(), response.success(), response.failedTicketIds());
                    }
                })
                .flatMap(response -> processUseCase.execute(response.orderId(), response.success()))
                .then();
    }
}