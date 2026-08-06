package com.example.parceltracker.adapter.out.courier;

import java.time.Instant;

/** Lenient ISO-8601 instant parsing for courier event feeds. */
final class TrackingTimestamps {

    private TrackingTimestamps() {
    }

    /**
     * Parses an ISO-8601 instant, falling back to {@code fallback} on null/blank/malformed
     * input. Keeping this per-event tolerant means one bad date doesn't discard the whole
     * tracking result — the event is kept, just timestamped with the fallback.
     */
    static Instant parseOrElse(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }
}
