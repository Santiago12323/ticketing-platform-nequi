package com.nequi.inventory.infrastructure.persistence.dynamo;

import com.nequi.inventory.domain.model.Event;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventEntity;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import com.nequi.inventory.infrastructure.persistence.mapper.EventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoEventRepositoryTest {

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @Mock
    private EventMapper entityMapper;

    @Mock
    private DynamoDbAsyncTable<EventEntity> table;

    @InjectMocks
    private DynamoEventRepository repository;

    private Event sampleEvent;
    private EventEntity sampleEntity;

    @BeforeEach
    void setup() {
        EventId eventId = EventId.of("11111111-1111-1111-1111-111111111111");

        sampleEvent = new Event(
                eventId,
                "Evento Test",
                "Bogotá",
                100,
                EventStatus.ACTIVE
        );

        sampleEntity = new EventEntity();
        sampleEntity.setEventId(eventId.value());
        sampleEntity.setName("Evento Test");
        sampleEntity.setLocation("Bogotá");
        sampleEntity.setTotalCapacity(100);
        sampleEntity.setStatus(EventStatus.ACTIVE);
        sampleEntity.setCreatedAt(Instant.now());
        sampleEntity.setUpdatedAt(Instant.now());

        when(enhancedClient.<EventEntity>table(anyString(), any())).thenReturn(table);
    }

    @Test
    void testFindById() {
        EventId eventId = EventId.of("11111111-1111-1111-1111-111111111111");

        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(sampleEntity));
        when(entityMapper.toDomain(sampleEntity)).thenReturn(sampleEvent);

        StepVerifier.create(repository.findById(eventId))
                .expectNext(sampleEvent)
                .verifyComplete();

        verify(table).getItem(any(Key.class));
        verify(entityMapper).toDomain(sampleEntity);
    }

    @Test
    void testSave() {
        when(entityMapper.toEntity(sampleEvent)).thenReturn(sampleEntity);
        when(table.putItem(sampleEntity)).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(repository.save(sampleEvent))
                .expectNext(sampleEvent)
                .verifyComplete();

        verify(table).putItem(sampleEntity);
        verify(entityMapper).toEntity(sampleEvent);
    }

    @Test
    void testExistsById_True() {
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(sampleEntity));

        StepVerifier.create(repository.existsById("123"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testExistsById_False() {
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(repository.existsById("999"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testFindAll() {
        PagePublisher<EventEntity> mockPagePublisher = mock(PagePublisher.class);

        SdkPublisher<EventEntity> sdkPublisher = SdkPublisher.adapt(
                reactor.core.publisher.Flux.just(sampleEntity)
        );

        when(mockPagePublisher.items()).thenReturn(sdkPublisher);
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(mockPagePublisher);
        when(entityMapper.toDomain(sampleEntity)).thenReturn(sampleEvent);

        StepVerifier.create(repository.findAll())
                .expectNext(sampleEvent)
                .verifyComplete();
    }
}