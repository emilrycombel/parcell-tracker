package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

/**
 * SPI for one tracking aggregator integration (TrackingMore, 17TRACK, Track123, …). Owns the
 * whole HTTP dance for its provider — some need more than one call — plus that provider's status
 * vocabulary → {@code ParcelStatus} mapping. Stateless; configuration (base-url, api-key) is
 * passed in per call by {@link AggregatorCourierClient}.
 */
public interface AggregatorProvider {

    /** Stable id used to select this provider via {@code AGGREGATOR_PROVIDER}. */
    String id();

    /** Endpoint used when {@code AGGREGATOR_BASE_URL} is not set. */
    String defaultBaseUrl();

    /**
     * Fetches tracking for one number. May throw — {@link AggregatorCourierClient} wraps the call
     * and maps any failure to {@link CourierTrackingResult#unknown()} (fail-soft).
     */
    CourierTrackingResult fetch(HttpClient http, String baseUrl, String apiKey,
                                String trackingNumber, ObjectMapper mapper) throws Exception;
}
