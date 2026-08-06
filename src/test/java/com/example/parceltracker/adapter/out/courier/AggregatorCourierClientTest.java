package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.testsupport.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregatorCourierClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private WireMockServer wm;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private AggregatorCourierClient enabledClient() {
        return new AggregatorCourierClient(TestConfig.of(Map.of(
                "base-url", wm.baseUrl(),
                "enabled", "true",
                "api-key", "test-key"
        )), MAPPER);
    }

    private AggregatorCourierClient disabledClient() {
        return new AggregatorCourierClient(TestConfig.of(Map.of(
                "base-url", wm.baseUrl(),
                "enabled", "false",
                "api-key", ""
        )), MAPPER);
    }

    @Test
    void disabled_supportsNothing_andFetchesUnknownWithoutHttp() {
        AggregatorCourierClient client = disabledClient();

        assertThat(client.supports(Courier.DPD, "x")).isFalse();
        assertThat(client.fetchTracking("x").status()).isEqualTo(ParcelStatus.UNKNOWN);
        assertThat(wm.getAllServeEvents()).isEmpty(); // never called the network
    }

    @Test
    void enabled_mapsStatusAndEvents() {
        wm.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "data": [
                                {
                                  "delivery_status": "transit",
                                  "origin_info": {
                                    "trackinfo": [
                                      {"Date": "2026-08-01T10:15:30Z", "StatusDescription": "In transit", "Details": "Hub"}
                                    ]
                                  }
                                }
                              ]
                            }
                            """)));

        CourierTrackingResult result = enabledClient().fetchTracking("00340000000000000001");

        assertThat(result.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).description()).isEqualTo("In transit");
        assertThat(result.events().get(0).location()).isEqualTo("Hub");
    }

    @Test
    void enabled_emptyData_returnsUnknown() {
        wm.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": []}")));

        assertThat(enabledClient().fetchTracking("x").status()).isEqualTo(ParcelStatus.UNKNOWN);
    }

    @Test
    void enabled_supportsAnyCourier() {
        assertThat(enabledClient().supports(Courier.DPD, "x")).isTrue();
    }

    @Test
    void selectedProvider_drivesTheRequestShape() {
        // provider=17track -> register + gettrackinfo POSTs, not TrackingMore's GET /get.
        wm.stubFor(post(urlPathEqualTo("/register")).willReturn(aResponse().withStatus(200).withBody("{}")));
        wm.stubFor(post(urlPathEqualTo("/gettrackinfo")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"data\": {\"accepted\": [{\"track_info\": {\"latest_status\": {\"status\": \"Delivered\"}}}]}}")));

        AggregatorCourierClient client = new AggregatorCourierClient(TestConfig.of(Map.of(
                "base-url", wm.baseUrl(),
                "enabled", "true",
                "api-key", "tok",
                "provider", "17track"
        )), MAPPER);

        assertThat(client.fetchTracking("RR123").status()).isEqualTo(ParcelStatus.DELIVERED);
        wm.verify(postRequestedFor(urlPathEqualTo("/gettrackinfo")).withHeader("17token", equalTo("tok")));
    }

    @Test
    void unknownProvider_failsFastAtConstruction() {
        assertThatThrownBy(() -> new AggregatorCourierClient(TestConfig.of(Map.of(
                "enabled", "true", "api-key", "k", "provider", "nope")), MAPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown aggregator provider");
    }
}
