package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;
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
import java.util.regex.Pattern;

/**
 * Free, unauthenticated InPost tracking endpoint:
 * GET https://api-shipx-pl.easypack24.net/v1/tracking/{trackingNumber}
 *
 * InPost tracking numbers are numeric, typically 24 digits — used as the
 * auto-detection heuristic when the caller doesn't specify a courier.
 */
public final class InPostCourierClient implements CourierClient {

    private static final Pattern INPOST_NUMBER = Pattern.compile("^\\d{20,26}$");

    // InPost's own status vocabulary -> our unified ParcelStatus.
    private static final Map<String, ParcelStatus> STATUS_MAP = Map.ofEntries(
            Map.entry("created", ParcelStatus.CREATED),
            Map.entry("offer_selected", ParcelStatus.CREATED),
            Map.entry("confirmed", ParcelStatus.CREATED),
            Map.entry("dispatched_by_sender", ParcelStatus.IN_TRANSIT),
            Map.entry("collected_from_sender", ParcelStatus.IN_TRANSIT),
            Map.entry("taken_by_courier", ParcelStatus.IN_TRANSIT),
            Map.entry("adopted_at_source_branch", ParcelStatus.IN_TRANSIT),
            Map.entry("sent_from_source_branch", ParcelStatus.IN_TRANSIT),
            Map.entry("adopted_at_sorting_center", ParcelStatus.IN_TRANSIT),
            Map.entry("sent_from_sorting_center", ParcelStatus.IN_TRANSIT),
            Map.entry("adopted_at_target_branch", ParcelStatus.IN_TRANSIT),
            Map.entry("out_for_delivery", ParcelStatus.OUT_FOR_DELIVERY),
            Map.entry("ready_to_pickup", ParcelStatus.READY_FOR_PICKUP),
            Map.entry("pickup_reminder_sent", ParcelStatus.READY_FOR_PICKUP),
            Map.entry("delivered", ParcelStatus.DELIVERED),
            Map.entry("returned_to_sender", ParcelStatus.RETURNED),
            Map.entry("canceled", ParcelStatus.EXCEPTION),
            Map.entry("undelivered_wrong_address", ParcelStatus.EXCEPTION),
            Map.entry("claimed", ParcelStatus.EXCEPTION)
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper;
    private final String baseUrl;

    public InPostCourierClient(Config config, ObjectMapper mapper) {
        this.mapper = mapper;
        this.baseUrl = config.get("base-url").asString().get();
    }

    public static boolean looksLikeInPostNumber(String trackingNumber) {
        return INPOST_NUMBER.matcher(trackingNumber).matches();
    }

    @Override
    public boolean supports(Courier courier, String trackingNumber) {
        if (courier == Courier.INPOST) {
            return true;
        }
        return courier == Courier.UNKNOWN && looksLikeInPostNumber(trackingNumber);
    }

    @Override
    public CourierTrackingResult fetchTracking(String trackingNumber) {
        URI uri = URI.create(baseUrl + "/" + trackingNumber);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return CourierTrackingResult.unknown();
            }
            if (response.statusCode() != 200) {
                return CourierTrackingResult.unknown();
            }
            JsonNode root = mapper.readTree(response.body());

            String rawStatus = root.path("status").asText(null);
            ParcelStatus status = rawStatus == null
                    ? ParcelStatus.UNKNOWN
                    : STATUS_MAP.getOrDefault(rawStatus, ParcelStatus.UNKNOWN);

            List<TrackingEvent> events = new ArrayList<>();
            JsonNode history = root.path("tracking_details");
            if (history.isArray()) {
                for (JsonNode entry : history) {
                    String status2 = entry.path("status").asText(null);
                    String dateStr = entry.path("datetime").asText(null);
                    Instant ts = TrackingTimestamps.parseOrElse(dateStr, Instant.now());
                    events.add(new TrackingEvent(
                            ts,
                            status2,
                            status2 == null ? "" : status2.replace('_', ' '),
                            entry.path("point_name").asText(null)
                    ));
                }
            }
            if (events.isEmpty() && rawStatus != null) {
                // Endpoint returned a current status with no history array — record at least that.
                events.add(new TrackingEvent(Instant.now(), rawStatus, rawStatus.replace('_', ' '), null));
            }

            return new CourierTrackingResult(status, events);
        } catch (Exception e) {
            return CourierTrackingResult.unknown();
        }
    }

    @Override
    public int priority() {
        return 0; // free + direct, always preferred over the aggregator
    }
}
