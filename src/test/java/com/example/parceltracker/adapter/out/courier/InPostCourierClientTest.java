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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/** Adapter integration test: exercises the real HTTP request/response wiring against a WireMock stub. */
class InPostCourierClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private WireMockServer wm;
    private InPostCourierClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        client = new InPostCourierClient(
                TestConfig.of(Map.of("base-url", wm.baseUrl() + "/v1/tracking")), MAPPER);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private void stub(String number, int status, String body) {
        wm.stubFor(get(urlPathEqualTo("/v1/tracking/" + number))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    void mapsStatusAndEvents_fromTrackingDetails() {
        stub("590123456789012345678901", 200, """
            {
              "status": "out_for_delivery",
              "tracking_details": [
                {"status": "confirmed", "datetime": "2026-08-01T08:00:00Z", "point_name": "Warszawa"},
                {"status": "out_for_delivery", "datetime": "2026-08-02T07:30:00Z", "point_name": "Warszawa Centrum"}
              ]
            }
            """);

        CourierTrackingResult result = client.fetchTracking("590123456789012345678901");

        assertThat(result.status()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).rawStatus()).isEqualTo("confirmed");
        assertThat(result.events().get(1).location()).isEqualTo("Warszawa Centrum");
    }

    @Test
    void mapsDeliveredStatus() {
        stub("111", 200, """
            {"status": "delivered", "tracking_details": []}
            """);

        assertThat(client.fetchTracking("111").status()).isEqualTo(ParcelStatus.DELIVERED);
    }

    @Test
    void unrecognizedRawStatus_mapsToUnknown_butStillRecordsAnEvent() {
        stub("222", 200, """
            {"status": "teleported", "tracking_details": []}
            """);

        CourierTrackingResult result = client.fetchTracking("222");

        assertThat(result.status()).isEqualTo(ParcelStatus.UNKNOWN);
        assertThat(result.events()).hasSize(1); // synthesises a single event from the current status
        assertThat(result.events().get(0).rawStatus()).isEqualTo("teleported");
    }

    @Test
    void notFound_returnsUnknown() {
        stub("404num", 404, "{}");
        assertThat(client.fetchTracking("404num")).usingRecursiveComparison()
                .isEqualTo(CourierTrackingResult.unknown());
    }

    @Test
    void serverError_returnsUnknown() {
        stub("500num", 500, "boom");
        assertThat(client.fetchTracking("500num").status()).isEqualTo(ParcelStatus.UNKNOWN);
    }

    @Test
    void supports_inpostAlways_andUnknownWhenNumberLooksInPost() {
        assertThat(client.supports(Courier.INPOST, "anything")).isTrue();
        assertThat(client.supports(Courier.UNKNOWN, "590123456789012345678901")).isTrue();
        assertThat(client.supports(Courier.UNKNOWN, "not-a-number")).isFalse();
        assertThat(client.supports(Courier.DPD, "590123456789012345678901")).isFalse();
    }
}
