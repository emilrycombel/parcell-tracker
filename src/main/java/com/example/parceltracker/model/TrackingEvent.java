package com.example.parceltracker.model;

import java.time.Instant;

public record TrackingEvent(
        Instant timestamp,
        String rawStatus,
        String description,
        String location
) {
}
