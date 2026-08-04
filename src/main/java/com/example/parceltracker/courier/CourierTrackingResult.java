package com.example.parceltracker.courier;

import com.example.parceltracker.model.ParcelStatus;
import com.example.parceltracker.model.TrackingEvent;

import java.util.List;

public record CourierTrackingResult(
        ParcelStatus status,
        List<TrackingEvent> events
) {
    public static CourierTrackingResult unknown() {
        return new CourierTrackingResult(ParcelStatus.UNKNOWN, List.of());
    }
}
