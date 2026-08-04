package com.example.parceltracker.live;

import com.example.parceltracker.adapter.out.courier.InPostCourierClient;
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
 * Opt-in smoke test against InPost's real public ShipX tracking endpoint. Skipped unless
 * LIVE_INPOST_TRACKING_NUMBER is set to a real number. Verifies the actual request/JSON
 * wiring end to end; never runs in the default `test` task (tagged "live").
 *
 * Run with: LIVE_INPOST_TRACKING_NUMBER=... ./gradlew liveTest
 */
@Tag("live")
class InPostLiveTest {

    private static final String BASE_URL = "https://api-shipx-pl.easypack24.net/v1/tracking";

    @Test
    void fetchesRealTrackingWithoutError() {
        String number = System.getenv("LIVE_INPOST_TRACKING_NUMBER");
        assumeTrue(number != null && !number.isBlank(),
                "Set LIVE_INPOST_TRACKING_NUMBER to run this live test");

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        InPostCourierClient client =
                new InPostCourierClient(TestConfig.of(Map.of("base-url", BASE_URL)), mapper);

        CourierTrackingResult result = client.fetchTracking(number);

        assertThat(result).isNotNull();
        assertThat(result.status()).isNotNull();
        System.out.println("[live] InPost " + number + " -> " + result.status()
                + " (" + result.events().size() + " events)");
    }
}
