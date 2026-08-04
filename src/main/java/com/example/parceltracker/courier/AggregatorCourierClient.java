package com.example.parceltracker.courier;

import com.example.parceltracker.model.Courier;
import com.example.parceltracker.model.ParcelStatus;
import com.example.parceltracker.model.TrackingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fallback for couriers InPost doesn't cover: DPD, DHL, GLS, Poczta Polska, UPS, FedEx.
 * Wired for TrackingMore's shape by default (https://www.trackingmore.com) — swap the
 * request/response mapping if you pick a different aggregator (ParcelsApp, 17TRACK, ...).
 * Disabled unless AGGREGATOR_ENABLED=true and an api-key is configured, so the service
 * still runs "free out of the box" for InPost-only use.
 */
public final class AggregatorCourierClient implements CourierClient {

    private static final Map<String, ParcelStatus> STATUS_MAP = Map.ofEntries(
            Map.entry("pending", ParcelStatus.CREATED),
            Map.entry("notfound", ParcelStatus.UNKNOWN),
            Map.entry("transit", ParcelStatus.IN_TRANSIT),
            Map.entry("pickup", ParcelStatus.OUT_FOR_DELIVERY),
            Map.entry("delivered", ParcelStatus.DELIVERED),
            Map.entry("expired", ParcelStatus.EXCEPTION),
            Map.entry("undelivered", ParcelStatus.EXCEPTION),
            Map.entry("exception", ParcelStatus.EXCEPTION)
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final boolean enabled;

    public AggregatorCourierClient(Config config, ObjectMapper mapper) {
        this.mapper = mapper;
        this.baseUrl = config.get("base-url").asString().get();
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
        URI uri = URI.create(baseUrl + "/get?tracking_numbers=" + trackingNumber);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Tracking-Api-Key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return CourierTrackingResult.unknown();
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return CourierTrackingResult.unknown();
            }
            JsonNode shipment = data.get(0);
            String rawStatus = shipment.path("delivery_status").asText(null);
            ParcelStatus status = rawStatus == null
                    ? ParcelStatus.UNKNOWN
                    : STATUS_MAP.getOrDefault(rawStatus, ParcelStatus.UNKNOWN);

            List<TrackingEvent> events = new ArrayList<>();
            JsonNode originInfo = shipment.path("origin_info").path("trackinfo");
            if (originInfo.isArray()) {
                for (JsonNode entry : originInfo) {
                    String dateStr = entry.path("Date").asText(null);
                    Instant ts = dateStr != null ? Instant.parse(dateStr) : Instant.now();
                    events.add(new TrackingEvent(
                            ts,
                            entry.path("StatusDescription").asText(null),
                            entry.path("StatusDescription").asText(""),
                            entry.path("Details").asText(null)
                    ));
                }
            }
            return new CourierTrackingResult(status, events);
        } catch (Exception e) {
            return CourierTrackingResult.unknown();
        }
    }

    @Override
    public int priority() {
        return 100; // last resort — paid, only used when InPost doesn't apply
    }
}
