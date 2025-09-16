package io.aeron.toys.shared;

public enum ToyStatus {
    UNKNOWN(0),
    PENDING(1),
    SOURCED(2),
    ASSEMBLED(3),
    COMPLETED(4);

    private final int value;

    ToyStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ToyStatus fromValue(int value) {
        for (ToyStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }

    public boolean isValid() {
        return this != UNKNOWN;
    }
}