package com.nequi.inventory.domain.model;

import com.nequi.inventory.domain.exception.BusinessException;
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
        validateCapacity(totalCapacity);
        this.eventId = eventId;
        this.name = name;
        this.location = location;
        this.totalCapacity = totalCapacity;
        this.status = eventStatus != null ? eventStatus : EventStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void validateSellable() {
        if (status != EventStatus.ACTIVE) {
            throw new BusinessException(
                    "EVENT_NOT_ACTIVE",
                    "El evento no está disponible para la venta. Estado actual: " + status
            );
        }
    }


    private void validateCapacity(int capacity) {
        if (capacity < 0) {
            throw new BusinessException(
                    "INVALID_EVENT_CAPACITY",
                    "La capacidad del evento no puede ser negativa: " + capacity
            );
        }
        if (capacity == 0) {
            throw new BusinessException(
                    "ZERO_EVENT_CAPACITY",
                    "Un evento debe tener al menos un cupo de capacidad."
            );
        }
    }

    public void cancelEvent() {
        this.status = EventStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == EventStatus.ACTIVE;
    }

    public void activate() {
        this.status = EventStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }
}