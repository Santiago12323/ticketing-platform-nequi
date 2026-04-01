package com.nequi.ticketing_service.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nequi.ticketing_service.domain.model.order.Order;
import com.nequi.ticketing_service.domain.port.out.OrderRepository;
import com.nequi.ticketing_service.domain.statemachine.OrderStateMachineFactory;
import com.nequi.ticketing_service.domain.valueobject.OrderId;
import com.nequi.ticketing_service.infrastructure.messaging.dto.response.InventoryResponse;
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

    private final OrderRepository repository;
    private final OrderStateMachineFactory smFactory;
    private final ObjectMapper objectMapper;

    @Value("${ticketing.statemachine.audit-enabled:false}")
    private boolean auditEnabled;

    @SqsListener("${aws.sqs.inventory-response-queue}")
    public void onMessage(String messageJson) {
        if (auditEnabled) {
            log.info("Received InventoryResponse from SQS: {}", messageJson);
        }

        processResponse(messageJson)
                .doOnError(err -> {
                    if (auditEnabled) {
                        log.error("Critical error processing inventory response: {}", err.getMessage(), err);
                    }
                })
                .onErrorResume(err -> {
                    if (auditEnabled) {
                        log.warn("Recovering from error while processing inventory response");
                    }
                    return Mono.empty();
                })
                .doFinally(signal -> {
                    if (auditEnabled) {
                        log.debug("Finished processing inventory response with signal: {}", signal);
                    }
                })
                .subscribe();
    }

    private Mono<Void> processResponse(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, InventoryResponse.class))
                .flatMap(response -> repository.findById(OrderId.of(response.orderId()))
                        .flatMap(orderData ->
                                smFactory.restore(orderData.getId().value(), orderData.getStatus())
                                        .map(sm -> new Order(
                                                orderData.getId(),
                                                orderData.getUserId(),
                                                orderData.getEventId(),
                                                orderData.getTotalPrice(),
                                                sm
                                        ))
                        )
                        .flatMap(order -> {
                            if (response.success()) {
                                return order.reserve()
                                        .flatMap(Order::startPayment)
                                        .flatMap(updatedOrder -> repository.save(updatedOrder, response.confirmedSeatIds()))
                                        .doOnSuccess(v -> {
                                            if (auditEnabled) {
                                                log.info("[{}] Inventory confirmed. Transitioned to RESERVED/PAYMENT",
                                                        order.getId().value());
                                            }
                                        });
                            } else {
                                return order.cancel()
                                        .flatMap(updatedOrder -> repository.save(updatedOrder, null))
                                        .doOnSuccess(v -> {
                                            if (auditEnabled) {
                                                log.warn("[{}] Inventory denied: {}. Order cancelled.",
                                                        order.getId().value(), response.errorCode());
                                            }
                                        });
                            }
                        })
                )
                .then();
    }
}
