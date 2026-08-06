package com.example.parceltracker.domain;

import java.time.Instant;

public record TrackingEvent(
        Instant timestamp,
        String rawStatus,
        String description,
        String location
) {
}
