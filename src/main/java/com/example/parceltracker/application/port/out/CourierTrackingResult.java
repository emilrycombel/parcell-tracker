package com.example.parceltracker.application.port.out;

import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;

import java.util.List;

/** Result of a courier lookup: a unified status plus the event history behind it. */
public record CourierTrackingResult(
        ParcelStatus status,
        List<TrackingEvent> events
) {
    public static CourierTrackingResult unknown() {
        return new CourierTrackingResult(ParcelStatus.UNKNOWN, List.of());
    }
}
