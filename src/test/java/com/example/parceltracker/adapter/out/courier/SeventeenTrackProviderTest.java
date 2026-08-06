package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.ParcelStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class SeventeenTrackProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient http = HttpClient.newHttpClient();
    private final SeventeenTrackProvider provider = new SeventeenTrackProvider();

    private WireMockServer wm;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        wm.stubFor(post(urlPathEqualTo("/register"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private void stubGetTrackInfo(String body) {
        wm.stubFor(post(urlPathEqualTo("/gettrackinfo"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private CourierTrackingResult fetch() throws Exception {
        return provider.fetch(http, wm.baseUrl(), "test-token", "RR123456785PL", MAPPER);
    }

    @Test
    void mapsStatusAndEvents_andSendsToken() throws Exception {
        stubGetTrackInfo("""
            {
              "code": 0,
              "data": {
                "accepted": [
                  {"number": "RR123456785PL", "track_info": {
                    "latest_status": {"status": "InTransit"},
                    "tracking": {"providers": [
                      {"events": [
                        {"time_iso": "2026-08-01T10:15:30Z", "stage": "InTransit",
                         "description": "Departed facility", "location": "Warszawa"}
                      ]}
                    ]}
                  }}
                ],
                "rejected": []
              }
            }
            """);

        CourierTrackingResult result = fetch();

        assertThat(result.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).description()).isEqualTo("Departed facility");
        assertThat(result.events().get(0).location()).isEqualTo("Warszawa");
        wm.verify(postRequestedFor(urlPathEqualTo("/gettrackinfo")).withHeader("17token", equalTo("test-token")));
    }

    @Test
    void mapsDelivered() throws Exception {
        stubGetTrackInfo("""
            {"data": {"accepted": [{"track_info": {"latest_status": {"status": "Delivered"}}}]}}
            """);
        assertThat(fetch().status()).isEqualTo(ParcelStatus.DELIVERED);
    }

    @Test
    void unknownRawStatus_mapsToUnknown() throws Exception {
        stubGetTrackInfo("""
            {"data": {"accepted": [{"track_info": {"latest_status": {"status": "Teleported"}}}]}}
            """);
        assertThat(fetch().status()).isEqualTo(ParcelStatus.UNKNOWN);
    }

    @Test
    void emptyAccepted_returnsUnknown() throws Exception {
        stubGetTrackInfo("""
            {"data": {"accepted": [], "rejected": [{"number": "RR123456785PL", "error": {"code": -18019902}}]}}
            """);
        assertThat(fetch().status()).isEqualTo(ParcelStatus.UNKNOWN);
        assertThat(fetch().events()).isEmpty();
    }

    @Test
    void serverError_returnsUnknown() throws Exception {
        wm.stubFor(post(urlPathEqualTo("/gettrackinfo")).willReturn(aResponse().withStatus(500)));
        assertThat(fetch().status()).isEqualTo(ParcelStatus.UNKNOWN);
    }
}
