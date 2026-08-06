package com.example.parceltracker.application.port.out;

import com.example.parceltracker.domain.Courier;

/**
 * Driven port for courier tracking. Hides how many couriers exist and which are free vs
 * paid — the application core just asks for a status. Implemented by an adapter that
 * dispatches to the right per-courier client (e.g. InPost direct, or a paid aggregator).
 */
public interface CourierGateway {

    /** Best-effort courier detection from the tracking number when the caller didn't specify one. */
    Courier detect(String trackingNumber);

    /** Fail-soft by contract: returns {@link CourierTrackingResult#unknown()} rather than throwing. */
    CourierTrackingResult fetchTracking(Courier courier, String trackingNumber);
}
