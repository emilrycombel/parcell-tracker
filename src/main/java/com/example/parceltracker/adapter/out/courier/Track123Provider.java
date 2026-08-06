package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Track123 (https://www.track123.com) open API. Auth via the {@code Track123-Api-Secret} header,
 * {@code POST /query} with body {@code {"trackNos":[<n>]}}.
 *
 * <p>Paths/status vocabulary are wired to the documented shape; validate against a live key
 * (see the aggregator-providers spec) before relying on the exact mappings.
 */
public final class Track123Provider implements AggregatorProvider {

    private static final Map<String, ParcelStatus> STATUS_MAP = Map.ofEntries(
            Map.entry("INFO_RECEIVED", ParcelStatus.CREATED),
            Map.entry("PENDING", ParcelStatus.CREATED),
            Map.entry("NOT_FOUND", ParcelStatus.UNKNOWN),
            Map.entry("TRANSIT", ParcelStatus.IN_TRANSIT),
            Map.entry("IN_TRANSIT", ParcelStatus.IN_TRANSIT),
            Map.entry("OUT_FOR_DELIVERY", ParcelStatus.OUT_FOR_DELIVERY),
            Map.entry("PICK_UP", ParcelStatus.READY_FOR_PICKUP),
            Map.entry("AVAILABLE_FOR_PICKUP", ParcelStatus.READY_FOR_PICKUP),
            Map.entry("DELIVERED", ParcelStatus.DELIVERED),
            Map.entry("EXCEPTION", ParcelStatus.EXCEPTION),
            Map.entry("EXPIRED", ParcelStatus.EXCEPTION),
            Map.entry("DELIVERY_FAILURE", ParcelStatus.EXCEPTION)
    );

    @Override
    public String id() {
        return "track123";
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.track123.com/gateway/open-api/tk/v2/track";
    }

    @Override
    public CourierTrackingResult fetch(HttpClient http, String baseUrl, String apiKey,
                                       String trackingNumber, ObjectMapper mapper) throws Exception {
        String body = mapper.writeValueAsString(Map.of("trackNos", List.of(trackingNumber)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/query"))
                .header("Track123-Api-Secret", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return CourierTrackingResult.unknown();
        }
        JsonNode content = mapper.readTree(response.body()).path("data").path("content");
        if (!content.isArray() || content.isEmpty()) {
            return CourierTrackingResult.unknown();
        }
        JsonNode item = content.get(0);
        String rawStatus = item.path("statusInfo").asText(null);
        ParcelStatus status = rawStatus == null
                ? ParcelStatus.UNKNOWN
                : STATUS_MAP.getOrDefault(rawStatus, ParcelStatus.UNKNOWN);

        List<TrackingEvent> events = new ArrayList<>();
        for (JsonNode provider : item.path("providersList")) {
            for (JsonNode detail : provider.path("trackingDetailList")) {
                Instant ts = TrackingTimestamps.parseOrElse(detail.path("eventTime").asText(null), Instant.now());
                events.add(new TrackingEvent(
                        ts,
                        detail.path("eventState").asText(null),
                        detail.path("eventDetail").asText(""),
                        detail.path("address").asText(null)));
            }
        }
        return new CourierTrackingResult(status, events);
    }
}
