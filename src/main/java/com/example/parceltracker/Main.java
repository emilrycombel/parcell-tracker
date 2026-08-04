package com.example.parceltracker;

import com.example.parceltracker.adapter.in.web.ParcelEndpoint;
import com.example.parceltracker.adapter.out.courier.AggregatorCourierClient;
import com.example.parceltracker.adapter.out.courier.CourierRouter;
import com.example.parceltracker.adapter.out.courier.InPostCourierClient;
import com.example.parceltracker.adapter.out.geocoding.NominatimGeocoder;
import com.example.parceltracker.adapter.out.persistence.DataSourceFactory;
import com.example.parceltracker.adapter.out.persistence.PostgresParcelRepository;
import com.example.parceltracker.application.port.out.CourierGateway;
import com.example.parceltracker.application.port.out.Geocoder;
import com.example.parceltracker.application.port.out.ParcelStore;
import com.example.parceltracker.application.service.ParcelService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Composition root. This is the only place that knows about concrete adapters — it wires
 * them into the application core (which sees only ports) and starts the web server.
 */
public final class Main {

    public static void main(String[] args) {
        Config config = Config.create();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        HikariDataSource dataSource = DataSourceFactory.create(config.get("db"));
        DataSourceFactory.applySchema(dataSource);

        // Driven adapters, referenced through their ports.
        ParcelStore store = new PostgresParcelRepository(dataSource, mapper);
        Geocoder geocoder = new NominatimGeocoder(config.get("geocoding"), mapper);
        CourierGateway courierGateway = new CourierRouter(List.of(
                new InPostCourierClient(config.get("courier.inpost"), mapper),
                new AggregatorCourierClient(config.get("courier.aggregator"), mapper)
        ));

        // Application core + driving adapter.
        ParcelService parcelService = new ParcelService(store, geocoder, courierGateway);
        ParcelEndpoint parcelEndpoint = new ParcelEndpoint(parcelService);

        MediaContext mediaContext = MediaContext.builder()
                .addMediaSupport(JacksonSupport.create(mapper))
                .build();

        WebServer server = WebServer.builder()
                .port(config.get("server.port").asInt().orElse(8080))
                .mediaContext(mediaContext)
                .routing(routing -> routing(routing, parcelEndpoint))
                .build()
                .start();

        System.out.println("Parcel Tracker started on http://localhost:" + server.port());
        System.out.println("OpenAPI spec:            http://localhost:" + server.port() + "/openapi.yaml");
        System.out.println("Register a parcel:  POST http://localhost:" + server.port() + "/api/v1/parcels");
    }

    private static void routing(HttpRouting.Builder routing, ParcelEndpoint parcelEndpoint) {
        routing.register("/api/v1", parcelEndpoint)
               .get("/health", (req, res) -> res.send("OK"))
               .get("/openapi.yaml", Main::serveOpenApiSpec);
    }

    private static void serveOpenApiSpec(io.helidon.webserver.http.ServerRequest req, io.helidon.webserver.http.ServerResponse res) {
        try (InputStream in = Main.class.getResourceAsStream("/META-INF/openapi.yaml")) {
            if (in == null) {
                res.status(io.helidon.http.Status.NOT_FOUND_404).send();
                return;
            }
            byte[] bytes = in.readAllBytes();
            res.headers().contentType(io.helidon.http.HttpMediaType.create("application/yaml"));
            res.send(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send();
        }
    }
}
