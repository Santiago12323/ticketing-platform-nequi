package com.nequi.inventory.domain.model;

import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.infrastructure.persistence.dynamo.entity.EventStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Event {

    private final EventId eventId;
    private final String name;
    private final String location;
    private final int totalCapacity;
    private EventStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Event(EventId eventId, String name, String location, int totalCapacity, EventStatus eventStatus) {
        this.eventId = eventId;
        this.name = name;
        this.location = location;
        this.totalCapacity = totalCapacity;
        this.status = eventStatus;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void validateSellable() {
        if (status != EventStatus.ACTIVE) {
            throw new IllegalStateException("Event is not available for sales");
        }
    }

    public void cancelEvent() {
        this.status = EventStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == EventStatus.ACTIVE;
    }
}