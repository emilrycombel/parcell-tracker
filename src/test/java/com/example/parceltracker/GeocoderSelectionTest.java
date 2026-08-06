package com.example.parceltracker;

import com.example.parceltracker.adapter.out.geocoding.DisabledGeocoder;
import com.example.parceltracker.adapter.out.geocoding.NominatimGeocoder;
import com.example.parceltracker.application.port.out.Geocoder;
import com.example.parceltracker.testsupport.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Geocoding is opt-in: the composition root only wires Nominatim when an endpoint is configured. */
class GeocoderSelectionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void disabled_whenBaseUrlEmpty() {
        Geocoder g = Main.geocoder(TestConfig.of(Map.of("base-url", "")), mapper);
        assertThat(g).isInstanceOf(DisabledGeocoder.class);
    }

    @Test
    void disabled_whenBaseUrlAbsent() {
        Geocoder g = Main.geocoder(TestConfig.of(Map.of()), mapper);
        assertThat(g).isInstanceOf(DisabledGeocoder.class);
    }

    @Test
    void nominatim_whenBaseUrlConfigured() {
        Geocoder g = Main.geocoder(TestConfig.of(Map.of(
                "base-url", "https://nominatim.example.org/search",
                "user-agent", "parcel-tracker-test/1.0")), mapper);
        assertThat(g).isInstanceOf(NominatimGeocoder.class);
    }
}
