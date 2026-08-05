package com.example.parceltracker.live;

import com.example.parceltracker.adapter.out.courier.AggregatorCourierClient;
import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.ParcelStatus;
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
 * registered shipment). AGGREGATOR_PROVIDER selects the integration (trackingmore | 17track |
 * track123, default trackingmore); AGGREGATOR_BASE_URL overrides its default endpoint. Tagged
 * "live" — never in `test`.
 *
 * Run with:
 *   AGGREGATOR_API_KEY=... LIVE_AGGREGATOR_TRACKING_NUMBER=... [AGGREGATOR_PROVIDER=17track] ./gradlew liveTest
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

        // Which integration to hit (default trackingmore); base-url empty -> provider default.
        String providerId = System.getenv().getOrDefault("AGGREGATOR_PROVIDER", "trackingmore");
        String baseUrl = System.getenv().getOrDefault("AGGREGATOR_BASE_URL", "");
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AggregatorCourierClient client = new AggregatorCourierClient(TestConfig.of(Map.of(
                "provider", providerId,
                "base-url", baseUrl,
                "enabled", "true",
                "api-key", apiKey
        )), mapper);

        CourierTrackingResult result = client.fetchTracking(number);

        assertThat(result).isNotNull();
        // A real, known shipment must resolve to a real status — not the fail-soft UNKNOWN.
        assertThat(result.status()).isNotEqualTo(ParcelStatus.UNKNOWN);
        System.out.println("[live] Aggregator[" + providerId + "] " + number + " -> " + result.status()
                + " (" + result.events().size() + " events)");
    }
}
