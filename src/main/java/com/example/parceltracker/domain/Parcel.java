package com.example.parceltracker.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Parcel(
        UUID id,
        String trackingNumber,
        String externalId,
        Courier courier,
        ParcelStatus status,
        Address deliveryAddress,
        GeoLocation geoLocation,
        List<TrackingEvent> events,
        Instant createdAt,
        Instant lastRefreshedAt,
        long version
) {
    public Parcel {
        // Defensive copy so a caller can't mutate the parcel's events after construction.
        events = events == null ? List.of() : List.copyOf(events);
    }

    public Parcel withGeoLocation(GeoLocation location) {
        return new Parcel(id, trackingNumber, externalId, courier, status, deliveryAddress,
                location, events, createdAt, lastRefreshedAt, version);
    }

    public Parcel withRefreshedTracking(ParcelStatus newStatus, List<TrackingEvent> newEvents, Instant refreshedAt) {
        return new Parcel(id, trackingNumber, externalId, courier, newStatus, deliveryAddress,
                geoLocation, newEvents, createdAt, refreshedAt, version);
    }

    /** Records that a refresh was attempted (advances lastRefreshedAt) without changing status/events. */
    public Parcel withRefreshAttemptAt(Instant refreshedAt) {
        return new Parcel(id, trackingNumber, externalId, courier, status, deliveryAddress,
                geoLocation, events, createdAt, refreshedAt, version);
    }

    /** Returns a copy stamped with the given optimistic-lock version. */
    public Parcel withVersion(long newVersion) {
        return new Parcel(id, trackingNumber, externalId, courier, status, deliveryAddress,
                geoLocation, events, createdAt, lastRefreshedAt, newVersion);
    }
}
