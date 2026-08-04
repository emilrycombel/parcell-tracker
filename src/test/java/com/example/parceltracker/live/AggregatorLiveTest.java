package com.example.parceltracker.live;

import com.example.parceltracker.adapter.out.courier.AggregatorCourierClient;
import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.testsupport.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in smoke test against the paid aggregator's real endpoint. Skipped unless both
 * AGGREGATOR_API_KEY and LIVE_AGGREGATOR_TRACKING_NUMBER are set (costs money / needs a
 * registered shipment). AGGREGATOR_BASE_URL overrides the default TrackingMore URL. Tagged
 * "live" — never in `test`.
 *
 * Run with:
 *   AGGREGATOR_API_KEY=... LIVE_AGGREGATOR_TRACKING_NUMBER=... ./gradlew liveTest
 */
@Tag("live")
class AggregatorLiveTest {

    @Test
    void fetchesRealTrackingWithoutError() {
        String apiKey = System.getenv("AGGREGATOR_API_KEY");
        String number = System.getenv("LIVE_AGGREGATOR_TRACKING_NUMBER");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Set AGGREGATOR_API_KEY to run this live test");
        assumeTrue(number != null && !number.isBlank(),
                "Set LIVE_AGGREGATOR_TRACKING_NUMBER to run this live test");

        String baseUrl = System.getenv().getOrDefault(
                "AGGREGATOR_BASE_URL", "https://api.trackingmore.com/v4/trackings");
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AggregatorCourierClient client = new AggregatorCourierClient(TestConfig.of(Map.of(
                "base-url", baseUrl,
                "enabled", "true",
                "api-key", apiKey
        )), mapper);

        CourierTrackingResult result = client.fetchTracking(number);

        assertThat(result).isNotNull();
        assertThat(result.status()).isNotNull();
        System.out.println("[live] Aggregator " + number + " -> " + result.status()
                + " (" + result.events().size() + " events)");
    }
}
