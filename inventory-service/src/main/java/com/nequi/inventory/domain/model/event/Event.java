package com.nequi.inventory.domain.model.event;


import com.nequi.inventory.domain.exception.InventoryException;
import com.nequi.inventory.domain.valueobject.EventId;
import com.nequi.inventory.domain.valueobject.SeatId;

import java.util.HashSet;
import java.util.Set;

public class Event {
    private final EventId id;
    private final String name;
    private final int capacity;
    private final Set<SeatId> availableSeats;
    private final Set<SeatId> reservedSeats;
    private final Set<SeatId> soldSeats;
    private Long version; // control de versionamiento

    public Event(EventId id, String name, int capacity) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.availableSeats = new HashSet<>();
        this.reservedSeats = new HashSet<>();
        this.soldSeats = new HashSet<>();
        for (int i = 1; i <= capacity; i++) {
            availableSeats.add(new SeatId("S" + i));
        }
        this.version = 0L;
    }

    public void reserveSeats(Set<SeatId> seats) {
        if (!availableSeats.containsAll(seats)) {
            throw new InventoryException("Seats not available");
        }
        availableSeats.removeAll(seats);
        reservedSeats.addAll(seats);
    }

    public void confirmSale(Set<SeatId> seats) {
        if (!reservedSeats.containsAll(seats)) {
            throw new InventoryException("Seats not reserved");
        }
        reservedSeats.removeAll(seats);
        soldSeats.addAll(seats);
    }

    public void releaseSeats(Set<SeatId> seats) {
        if (!reservedSeats.containsAll(seats)) {
            throw new InventoryException("Seats not reserved");
        }
        reservedSeats.removeAll(seats);
        availableSeats.addAll(seats);
    }

    public int getAvailableCount() {
        return availableSeats.size();
    }

    public Long getVersion() {
        return version;
    }

    public void incrementVersion() {
        this.version++;
    }

    public EventId getId() {
        return id;
    }
}
