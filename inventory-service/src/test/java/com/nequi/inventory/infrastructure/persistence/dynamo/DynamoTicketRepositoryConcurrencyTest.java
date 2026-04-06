package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.OrderId;
import com.nequi.inventory.domain.valueobject.TicketId;
import com.nequi.inventory.infrastructure.messaging.sqs.dto.response.InventoryResponse;
import com.nequi.inventory.infrastructure.messaging.sqs.enums.Type;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Tag("integration")
class DynamoTicketRepositoryConcurrencyTest {

    @Autowired
    private DynamoTicketRepository ticketRepository;

    @Autowired
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    private static final String TICKET_TABLE = "Ticket";
    private static final String EVENT_TABLE = "Event";
    private static final int CONCURRENT_USERS = 25;

    @Test
    @DisplayName("Validacion de consistencia atomica bajo alta concurrencia (25 usuarios)")
    void shouldHandleHighConcurrencyRaceCondition() {
        final EventId eventId = EventId.of(UUID.randomUUID().toString());
        final TicketId ticketId = TicketId.of(UUID.randomUUID().toString());
        final Set<String> ticketIds = Set.of(ticketId.value());

        log.info("Iniciando secuencia de prueba - Evento: {} - Ticket: {}", eventId.value(), ticketId.value());

        setupEnvironment(eventId, ticketId)
                .doOnSuccess(v -> log.info("Entorno de base de datos inicializado"))
                .block(Duration.ofSeconds(10));

        List<InventoryResponse> results = Flux.range(1, CONCURRENT_USERS)
                .map(i -> OrderId.of(UUID.randomUUID().toString()))
                .flatMap(orderId ->
                        ticketRepository.reserveAll(eventId, ticketIds, orderId)
                                .doOnNext(res -> log.debug("Procesada orden {}: success={}", orderId.value(), res.success()))
                                .onErrorResume(e -> {
                                    return Mono.just(InventoryResponse.failure(orderId.value(), List.of(ticketId.value()), Type.RESERVE));
                                })
                )
                .collectList()
                .block(Duration.ofSeconds(30));

        Map<Boolean, Long> summary = results.stream()
                .collect(Collectors.partitioningBy(InventoryResponse::success, Collectors.counting()));

        long actualSuccess = summary.getOrDefault(true, 0L);
        long actualFailures = summary.getOrDefault(false, 0L);

        log.info("Resultados de concurrencia - Exitos: {} - Fallos: {}", actualSuccess, actualFailures);

        assertEquals(1, actualSuccess, "Invariante de negocio fallida: Se permitio mas de una reserva exitosa");
        assertEquals(CONCURRENT_USERS - 1, actualFailures, "Error en el control de rechazos por estado de ticket");

        cleanUp(eventId, ticketId)
                .doOnSuccess(v -> log.info("Limpieza de tablas completada satisfactoriamente"))
                .subscribe();
    }

    private Mono<Void> setupEnvironment(EventId eventId, TicketId ticketId) {
        return Mono.zip(
                saveItem(EVENT_TABLE, Map.of(
                        "eventId", AttributeValue.builder().s(eventId.value()).build(),
                        "status", AttributeValue.builder().s("ACTIVE").build(),
                        "name", AttributeValue.builder().s("High Load Test").build()
                )),
                saveItem(TICKET_TABLE, Map.of(
                        "ticketId", AttributeValue.builder().s(ticketId.value()).build(),
                        "eventId", AttributeValue.builder().s(eventId.value()).build(),
                        "status", AttributeValue.builder().s("AVAILABLE").build()
                ))
        ).then();
    }

    private Mono<Void> saveItem(String tableName, Map<String, AttributeValue> item) {
        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.putItem(request))
                .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(200)))
                .then();
    }

    private Mono<Void> cleanUp(EventId eventId, TicketId ticketId) {
        Map<String, AttributeValue> eventKey = Collections.singletonMap("eventId", AttributeValue.builder().s(eventId.value()).build());
        Map<String, AttributeValue> ticketKey = Collections.singletonMap("ticketId", AttributeValue.builder().s(ticketId.value()).build());

        return Mono.zip(
                deleteItem(EVENT_TABLE, eventKey),
                deleteItem(ticketKey)
        ).then();
    }

    private Mono<Void> deleteItem(String tableName, Map<String, AttributeValue> key) {
        return Mono.fromFuture(dynamoDbAsyncClient.deleteItem(
                        DeleteItemRequest.builder().tableName(tableName).key(key).build()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Void> deleteItem(Map<String, AttributeValue> key) {
        return deleteItem(TICKET_TABLE, key);
    }
}