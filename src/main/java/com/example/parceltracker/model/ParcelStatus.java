package com.example.parceltracker.model;

/** Unified status, mapped from each courier's own vocabulary. */
public enum ParcelStatus {
    REGISTERED,
    CREATED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    READY_FOR_PICKUP,
    DELIVERED,
    EXCEPTION,
    RETURNED,
    UNKNOWN
}
