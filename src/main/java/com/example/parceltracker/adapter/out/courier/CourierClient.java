package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Courier;

/**
 * SPI for a single courier, internal to the courier adapter. {@link CourierRouter} holds a
 * list of these and dispatches to the first that {@link #supports} the request — that router
 * is what the application core sees (as the {@code CourierGateway} port). Adding a courier =
 * a new implementation of this interface, registered in the composition root.
 */
public interface CourierClient {

    /** Whether this client can (and should) handle the given courier/tracking number combo. */
    boolean supports(Courier courier, String trackingNumber);

    CourierTrackingResult fetchTracking(String trackingNumber);

    /** Lower runs first in the router. InPost (free, direct) should win over the paid fallback. */
    int priority();
}
