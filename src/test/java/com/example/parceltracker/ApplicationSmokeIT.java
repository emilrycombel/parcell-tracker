package com.example.parceltracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end startup smoke test: boots the whole application via {@link Main#start} against a
 * real Postgres (Testcontainers) and drives it over HTTP. Exercises the wiring that unit tests
 * (which use isolated config) never touch — config resolution incl. {@code ${VAR:default}},
 * datasource + schema, the web server, media, and routing.
 *
 * <p>External courier/geocoding base-urls point at a dead port so the test stays hermetic and
 * fast: those calls fail-soft, so registration still succeeds without status/coordinates.
 * Skipped automatically when no Docker daemon is available.
 */
@Testcontainers(disabledWithoutDocker = true)
class ApplicationSmokeIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("parcels").withUsername("parcels").withPassword("parcels");

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Main.RunningApp app;
    private static String baseUrl;

    @BeforeAll
    static void boot() {
        Config config = Config.builder()
                .addFilter(new EnvSubstitutionConfigFilter())
                // Overrides (highest priority), then the real application.yaml for everything else.
                .addSource(ConfigSources.create(Map.of(
                        "server.host", "localhost",
                        "server.port", "0", // ephemeral
                        "db.url", POSTGRES.getJdbcUrl(),
                        "db.user", POSTGRES.getUsername(),
                        "db.password", POSTGRES.getPassword(),
                        // Dead-end external services: fail-soft, keeps the smoke test offline.
                        "geocoding.base-url", "http://localhost:1/search",
                        "courier.inpost.base-url", "http://localhost:1/tracking")))
                .addSource(ConfigSources.classpath("application.yaml"))
                .build();

        app = Main.start(config);
        baseUrl = "http://localhost:" + app.server().port();
    }

    @AfterAll
    static void shutdown() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void health_isServedAtRoot() throws Exception {
        HttpResponse<String> res = get("/health");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("OK");
    }

    @Test
    void openapiSpec_isServed() throws Exception {
        HttpResponse<String> res = get("/openapi.yaml");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("openapi:");
    }

    @Test
    void register_then_get_then_list_roundTripsThroughRealWiring() throws Exception {
        String body = """
            {
              "trackingNumber": "590123456789012345678901",
              "externalId": "ORDER-SMOKE-1",
              "deliveryAddress": {
                "street": "Marszałkowska", "houseNumber": "1",
                "city": "Warszawa", "postalCode": "00-001", "country": "PL"
              }
            }
            """;
        HttpResponse<String> reg = post("/api/v1/parcels", body);
        assertThat(reg.statusCode()).isEqualTo(201);
        JsonNode created = MAPPER.readTree(reg.body());
        assertThat(created.get("status").asText()).isEqualTo("REGISTERED"); // courier dead-ended → fail-soft
        assertThat(created.get("geoLocation").isNull()).isTrue();           // geocoder dead-ended → fail-soft
        assertThat(created.get("externalId").asText()).isEqualTo("ORDER-SMOKE-1");
        String id = created.get("id").asText();

        HttpResponse<String> one = get("/api/v1/parcels/" + id);
        assertThat(one.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(one.body()).get("status").asText()).isEqualTo("REGISTERED");

        HttpResponse<String> byOrder = get("/api/v1/parcels?externalId=ORDER-SMOKE-1");
        assertThat(byOrder.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(byOrder.body()).size()).isEqualTo(1);
    }

    @Test
    void malformedBody_returns400() throws Exception {
        HttpResponse<String> res = post("/api/v1/parcels", "{ not json");
        assertThat(res.statusCode()).isEqualTo(400);
    }

    @Test
    void invalidListParam_returns400() throws Exception {
        assertThat(get("/api/v1/parcels?size=abc").statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/parcels?size=99999").statusCode()).isEqualTo(400);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String json) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
