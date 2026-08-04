package com.example.parceltracker.courier;

import com.example.parceltracker.model.Courier;

import java.util.Comparator;
import java.util.List;

public final class CourierRouter {

    private final List<CourierClient> clients;

    public CourierRouter(List<CourierClient> clients) {
        this.clients = clients.stream()
                .sorted(Comparator.comparingInt(CourierClient::priority))
                .toList();
    }

    /** Best-effort courier detection when the caller doesn't specify one. */
    public Courier detect(String trackingNumber) {
        if (InPostCourierClient.looksLikeInPostNumber(trackingNumber)) {
            return Courier.INPOST;
        }
        return Courier.UNKNOWN;
    }

    public CourierTrackingResult fetchTracking(Courier courier, String trackingNumber) {
        return clients.stream()
                .filter(c -> c.supports(courier, trackingNumber))
                .findFirst()
                .map(c -> c.fetchTracking(trackingNumber))
                .orElse(CourierTrackingResult.unknown());
    }
}
