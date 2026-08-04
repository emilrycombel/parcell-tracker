package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierGateway;
import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Courier;

import java.util.Comparator;
import java.util.List;

/**
 * Courier adapter implementing the {@link CourierGateway} port: dispatches to the
 * priority-ordered {@link CourierClient} that claims the request. A plain sorted-list scan,
 * fine at this scale; revisit if the courier count grows enough to need a real registry.
 */
public final class CourierRouter implements CourierGateway {

    private final List<CourierClient> clients;

    public CourierRouter(List<CourierClient> clients) {
        this.clients = clients.stream()
                .sorted(Comparator.comparingInt(CourierClient::priority))
                .toList();
    }

    @Override
    public Courier detect(String trackingNumber) {
        if (InPostCourierClient.looksLikeInPostNumber(trackingNumber)) {
            return Courier.INPOST;
        }
        return Courier.UNKNOWN;
    }

    @Override
    public CourierTrackingResult fetchTracking(Courier courier, String trackingNumber) {
        return clients.stream()
                .filter(c -> c.supports(courier, trackingNumber))
                .findFirst()
                .map(c -> c.fetchTracking(trackingNumber))
                .orElse(CourierTrackingResult.unknown());
    }
}
