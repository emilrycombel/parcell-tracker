package com.example.parceltracker.adapter.out.geocoding;

import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.testsupport.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NominatimGeocoderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Address ADDRESS =
            new Address("Marszałkowska", "1", null, "Warszawa", "00-001", "PL");

    private WireMockServer wm;
    private NominatimGeocoder geocoder;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        geocoder = new NominatimGeocoder(TestConfig.of(Map.of(
                "base-url", wm.baseUrl() + "/search",
                "user-agent", "parcel-tracker-test/1.0"
        )), MAPPER);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void returnsCoordinates_onHit() {
        wm.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"lat\": \"52.2296756\", \"lon\": \"21.0122287\"}]")));

        Optional<GeoLocation> result = geocoder.geocode(ADDRESS);

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isCloseTo(52.2296756, within(1e-9));
        assertThat(result.get().longitude()).isCloseTo(21.0122287, within(1e-9));
        assertThat(result.get().provider()).isEqualTo("nominatim");
    }

    @Test
    void emptyResults_returnEmpty() {
        wm.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(geocoder.geocode(ADDRESS)).isEmpty();
    }

    @Test
    void serverError_returnsEmpty_failSoft() {
        wm.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(500).withBody("nope")));

        assertThat(geocoder.geocode(ADDRESS)).isEmpty();
    }

    @Test
    void sendsUserAgentHeader() {
        wm.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"lat\": \"52.0\", \"lon\": \"21.0\"}]")));

        geocoder.geocode(ADDRESS);

        wm.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/search"))
                .withHeader("User-Agent", com.github.tomakehurst.wiremock.client.WireMock.equalTo("parcel-tracker-test/1.0")));
    }
}
