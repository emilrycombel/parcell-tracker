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
 * 17TRACK API v2.2 (https://api.17track.net). Auth via the {@code 17token} header. Register-then-poll:
 * a best-effort {@code POST /register} (idempotent for an already-tracked number) followed by
 * {@code POST /gettrackinfo}, both with body {@code [{"number": <n>}]}.
 *
 * <p>Paths/status vocabulary are wired to the documented v2.2 shape; validate against a live key
 * (see the aggregator-providers spec) before relying on the exact mappings.
 */
public final class SeventeenTrackProvider implements AggregatorProvider {

    // 17TRACK main status (e_status / latest_status.status) -> unified.
    private static final Map<String, ParcelStatus> STATUS_MAP = Map.ofEntries(
            Map.entry("NotFound", ParcelStatus.UNKNOWN),
            Map.entry("InfoReceived", ParcelStatus.CREATED),
            Map.entry("InTransit", ParcelStatus.IN_TRANSIT),
            Map.entry("Expired", ParcelStatus.EXCEPTION),
            Map.entry("AvailableForPickup", ParcelStatus.READY_FOR_PICKUP),
            Map.entry("OutForDelivery", ParcelStatus.OUT_FOR_DELIVERY),
            Map.entry("DeliveryFailure", ParcelStatus.EXCEPTION),
            Map.entry("Delivered", ParcelStatus.DELIVERED),
            Map.entry("Exception", ParcelStatus.EXCEPTION)
    );

    @Override
    public String id() {
        return "17track";
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.17track.net/track/v2.2";
    }

    @Override
    public CourierTrackingResult fetch(HttpClient http, String baseUrl, String apiKey,
                                       String trackingNumber, ObjectMapper mapper) throws Exception {
        String body = mapper.writeValueAsString(List.of(Map.of("number", trackingNumber)));

        // Best-effort registration so the number is tracked; ignore the outcome.
        http.send(post(baseUrl + "/register", apiKey, body), HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> response =
                http.send(post(baseUrl + "/gettrackinfo", apiKey, body), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return CourierTrackingResult.unknown();
        }

        JsonNode accepted = mapper.readTree(response.body()).path("data").path("accepted");
        if (!accepted.isArray() || accepted.isEmpty()) {
            return CourierTrackingResult.unknown();
        }
        JsonNode trackInfo = accepted.get(0).path("track_info");
        String rawStatus = trackInfo.path("latest_status").path("status").asText(null);
        ParcelStatus status = rawStatus == null
                ? ParcelStatus.UNKNOWN
                : STATUS_MAP.getOrDefault(rawStatus, ParcelStatus.UNKNOWN);

        List<TrackingEvent> events = new ArrayList<>();
        for (JsonNode provider : trackInfo.path("tracking").path("providers")) {
            for (JsonNode event : provider.path("events")) {
                Instant ts = TrackingTimestamps.parseOrElse(event.path("time_iso").asText(null), Instant.now());
                events.add(new TrackingEvent(
                        ts,
                        event.path("stage").asText(null),
                        event.path("description").asText(""),
                        event.path("location").asText(null)));
            }
        }
        return new CourierTrackingResult(status, events);
    }

    private static HttpRequest post(String url, String apiKey, String body) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("17token", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
