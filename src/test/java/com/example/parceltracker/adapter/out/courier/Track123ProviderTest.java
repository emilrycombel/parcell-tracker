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

class Track123ProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient http = HttpClient.newHttpClient();
    private final Track123Provider provider = new Track123Provider();

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

    private void stubQuery(String body) {
        wm.stubFor(post(urlPathEqualTo("/query"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private CourierTrackingResult fetch() throws Exception {
        return provider.fetch(http, wm.baseUrl(), "secret-key", "TN123", MAPPER);
    }

    @Test
    void mapsStatusAndEvents_andSendsApiSecret() throws Exception {
        stubQuery("""
            {
              "code": "00000",
              "data": {"content": [
                {"trackNo": "TN123", "statusInfo": "IN_TRANSIT", "providersList": [
                  {"trackingDetailList": [
                    {"eventTime": "2026-08-01T10:15:30Z", "eventState": "IN_TRANSIT",
                     "eventDetail": "Arrived at hub", "address": "Kraków"}
                  ]}
                ]}
              ]}
            }
            """);

        CourierTrackingResult result = fetch();

        assertThat(result.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).description()).isEqualTo("Arrived at hub");
        assertThat(result.events().get(0).location()).isEqualTo("Kraków");
        wm.verify(postRequestedFor(urlPathEqualTo("/query")).withHeader("Track123-Api-Secret", equalTo("secret-key")));
    }

    @Test
    void mapsDelivered() throws Exception {
        stubQuery("""
            {"data": {"content": [{"statusInfo": "DELIVERED"}]}}
            """);
        assertThat(fetch().status()).isEqualTo(ParcelStatus.DELIVERED);
    }

    @Test
    void emptyContent_returnsUnknown() throws Exception {
        stubQuery("""
            {"code": "00000", "data": {"content": []}}
            """);
        assertThat(fetch().status()).isEqualTo(ParcelStatus.UNKNOWN);
    }

    @Test
    void serverError_returnsUnknown() throws Exception {
        wm.stubFor(post(urlPathEqualTo("/query")).willReturn(aResponse().withStatus(500)));
        assertThat(fetch().status()).isEqualTo(ParcelStatus.UNKNOWN);
    }
}
