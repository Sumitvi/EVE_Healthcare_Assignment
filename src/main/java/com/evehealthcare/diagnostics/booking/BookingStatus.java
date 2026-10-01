package com.evehealthcare.diagnostics.booking;


public enum BookingStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED;


    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == FAILED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case FAILED, CANCELLED -> false;
        };
    }
}
