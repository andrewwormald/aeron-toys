package io.aeron.toys.shared;

import java.time.Instant;

public class Toy {
    private final long id;
    private final long customerId;
    private ToyStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Toy(long id, long customerId, ToyStatus status) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public ToyStatus getStatus() {
        return status;
    }

    public void setStatus(ToyStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return String.format("Toy{id=%d, customerId=%d, status=%s, createdAt=%s, updatedAt=%s}",
                id, customerId, status, createdAt, updatedAt);
    }
}