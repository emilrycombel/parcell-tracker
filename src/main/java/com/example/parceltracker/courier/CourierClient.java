package com.example.parceltracker.courier;

import com.example.parceltracker.model.Courier;

public interface CourierClient {

    /** Whether this client can (and should) handle the given courier/tracking number combo. */
    boolean supports(Courier courier, String trackingNumber);

    CourierTrackingResult fetchTracking(String trackingNumber);

    /** Lower runs first in the router. InPost (free, direct) should win over the paid fallback. */
    int priority();
}
