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

/** TrackingMore (https://www.trackingmore.com) — GET /get?tracking_numbers=, Tracking-Api-Key header. */
public final class TrackingMoreProvider implements AggregatorProvider {

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

    @Override
    public String id() {
        return "trackingmore";
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.trackingmore.com/v4/trackings";
    }

    @Override
    public CourierTrackingResult fetch(HttpClient http, String baseUrl, String apiKey,
                                       String trackingNumber, ObjectMapper mapper) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/get?tracking_numbers=" + trackingNumber))
                .header("Tracking-Api-Key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return CourierTrackingResult.unknown();
        }
        JsonNode data = mapper.readTree(response.body()).path("data");
        if (!data.isArray() || data.isEmpty()) {
            return CourierTrackingResult.unknown();
        }
        JsonNode shipment = data.get(0);
        String rawStatus = shipment.path("delivery_status").asText(null);
        ParcelStatus status = rawStatus == null
                ? ParcelStatus.UNKNOWN
                : STATUS_MAP.getOrDefault(rawStatus, ParcelStatus.UNKNOWN);

        List<TrackingEvent> events = new ArrayList<>();
        JsonNode trackinfo = shipment.path("origin_info").path("trackinfo");
        if (trackinfo.isArray()) {
            for (JsonNode entry : trackinfo) {
                Instant ts = TrackingTimestamps.parseOrElse(entry.path("Date").asText(null), Instant.now());
                events.add(new TrackingEvent(
                        ts,
                        entry.path("StatusDescription").asText(null),
                        entry.path("StatusDescription").asText(""),
                        entry.path("Details").asText(null)));
            }
        }
        return new CourierTrackingResult(status, events);
    }
}
