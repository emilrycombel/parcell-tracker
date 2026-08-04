package com.example.parceltracker.live;

import com.example.parceltracker.adapter.out.geocoding.NominatimGeocoder;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.testsupport.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in smoke test against the real Nominatim service. Skipped unless LIVE_GEOCODING is set
 * (hitting the public service should be deliberate — it has a strict usage policy). Verifies a
 * known Warsaw address geocodes to coordinates inside Poland. Tagged "live" — never in `test`.
 *
 * Run with: LIVE_GEOCODING=1 ./gradlew liveTest
 */
@Tag("live")
class NominatimLiveTest {

    @Test
    void geocodesKnownAddress_intoPoland() {
        assumeTrue(System.getenv("LIVE_GEOCODING") != null,
                "Set LIVE_GEOCODING=1 to run this live test (respects Nominatim usage policy)");

        NominatimGeocoder geocoder = new NominatimGeocoder(TestConfig.of(Map.of(
                "base-url", "https://nominatim.openstreetmap.org/search",
                "user-agent", "parcel-tracker-livetest/1.0 (github.com/emilrycombel/parcell-tracker)",
                "min-interval-ms", "1000"
        )), new ObjectMapper());

        Optional<GeoLocation> result = geocoder.geocode(
                new Address("Marszałkowska", "1", null, "Warszawa", "00-001", "PL"));

        assertThat(result).isPresent();
        GeoLocation geo = result.get();
        // Poland bounding box, roughly.
        assertThat(geo.latitude()).isBetween(49.0, 55.0);
        assertThat(geo.longitude()).isBetween(14.0, 24.5);
        System.out.println("[live] Nominatim -> " + geo.latitude() + ", " + geo.longitude());
    }
}
