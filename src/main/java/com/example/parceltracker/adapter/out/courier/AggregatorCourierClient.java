package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Courier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.config.Config;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Catch-all fallback for couriers InPost doesn't cover (DPD, DHL, GLS, Poczta Polska, UPS, FedEx).
 * Delegates the actual request/response to one configured {@link AggregatorProvider}
 * (TrackingMore, 17TRACK, Track123), selected by {@code AGGREGATOR_PROVIDER}. Disabled unless
 * {@code AGGREGATOR_ENABLED=true} and an api-key is set, so the service still runs free out of the
 * box for InPost-only use.
 */
public final class AggregatorCourierClient implements CourierClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper;
    private final AggregatorProvider provider;
    private final String baseUrl;
    private final String apiKey;
    private final boolean enabled;

    public AggregatorCourierClient(Config config, ObjectMapper mapper) {
        this.mapper = mapper;
        // Fail-fast on an unknown provider id (misconfiguration surfaced at startup).
        this.provider = AggregatorProviders.byId(config.get("provider").asString().orElse("trackingmore"));
        String configuredBaseUrl = config.get("base-url").asString().orElse("");
        this.baseUrl = configuredBaseUrl.isBlank() ? provider.defaultBaseUrl() : configuredBaseUrl;
        this.apiKey = config.get("api-key").asString().orElse("");
        this.enabled = config.get("enabled").asBoolean().orElse(false) && !apiKey.isBlank();
    }

    @Override
    public boolean supports(Courier courier, String trackingNumber) {
        // Catch-all fallback: anything InPost didn't already claim.
        return enabled;
    }

    @Override
    public CourierTrackingResult fetchTracking(String trackingNumber) {
        if (!enabled) {
            return CourierTrackingResult.unknown();
        }
        try {
            return provider.fetch(http, baseUrl, apiKey, trackingNumber, mapper);
        } catch (Exception e) {
            return CourierTrackingResult.unknown();
        }
    }

    @Override
    public int priority() {
        return 100; // last resort — paid, only used when InPost doesn't apply
    }
}
