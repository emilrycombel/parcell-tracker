package com.example.parceltracker.adapter.out.geocoding;

import com.example.parceltracker.application.port.out.Geocoder;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.GeoLocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.config.Config;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * {@link Geocoder} adapter backed by Nominatim (OpenStreetMap): free, no API key. Usage
 * policy caps this at ~1 req/sec and requires a real User-Agent — fine at 1k parcels/month
 * since we only geocode once per address (result is cached on the parcel row).
 */
public final class NominatimGeocoder implements Geocoder {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String userAgent;
    /** Minimum spacing between outbound requests — Nominatim's policy is ~1 req/sec. */
    private final long minIntervalMillis;
    private final Object rateGate = new Object();
    private long lastRequestMillis = 0L;

    public NominatimGeocoder(Config config, ObjectMapper mapper) {
        this.mapper = mapper;
        this.baseUrl = config.get("base-url").asString().get();
        this.userAgent = config.get("user-agent").asString().get();
        this.minIntervalMillis = config.get("min-interval-ms").asInt().orElse(1000);
    }

    @Override
    public Optional<GeoLocation> geocode(Address address) {
        awaitRateLimit();
        String query = URLEncoder.encode(address.toQueryString(), StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "?q=" + query + "&format=json&limit=1&addressdetails=0");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode results = mapper.readTree(response.body());
            if (!results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = results.get(0);
            double lat = Double.parseDouble(first.get("lat").asText());
            double lon = Double.parseDouble(first.get("lon").asText());
            return Optional.of(new GeoLocation(lat, lon, "nominatim", Instant.now()));
        } catch (Exception e) {
            // Geocoding failure should not block parcel registration/refresh.
            return Optional.empty();
        }
    }

    /**
     * Blocks until at least {@code minIntervalMillis} has elapsed since the previous request,
     * serializing concurrent callers so we stay within Nominatim's usage policy. Cheap here:
     * geocoding runs on virtual threads and only happens once per address.
     */
    private void awaitRateLimit() {
        if (minIntervalMillis <= 0) {
            return;
        }
        synchronized (rateGate) {
            long now = System.currentTimeMillis();
            long waitMillis = lastRequestMillis + minIntervalMillis - now;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestMillis = System.currentTimeMillis();
        }
    }
}
