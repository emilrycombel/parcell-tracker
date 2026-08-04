package com.example.parceltracker.model;

import java.time.Instant;

public record GeoLocation(
        double latitude,
        double longitude,
        String provider,
        Instant geocodedAt
) {
}
