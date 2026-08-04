package com.example.parceltracker.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Parcel(
        UUID id,
        String trackingNumber,
        Courier courier,
        ParcelStatus status,
        Address deliveryAddress,
        GeoLocation geoLocation,
        List<TrackingEvent> events,
        Instant createdAt,
        Instant lastRefreshedAt
) {
    public Parcel withGeoLocation(GeoLocation location) {
        return new Parcel(id, trackingNumber, courier, status, deliveryAddress,
                location, events, createdAt, lastRefreshedAt);
    }

    public Parcel withRefreshedTracking(ParcelStatus newStatus, List<TrackingEvent> newEvents, Instant refreshedAt) {
        return new Parcel(id, trackingNumber, courier, newStatus, deliveryAddress,
                geoLocation, newEvents, createdAt, refreshedAt);
    }
}
