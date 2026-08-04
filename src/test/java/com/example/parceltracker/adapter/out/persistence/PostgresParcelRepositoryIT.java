package com.example.parceltracker.adapter.out.persistence;

import com.example.parceltracker.application.port.out.DuplicateParcelException;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;
import com.example.parceltracker.testsupport.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence-adapter integration test against a real Postgres via Testcontainers.
 * Skipped automatically when no Docker daemon is available (e.g. this CI sandbox);
 * runs wherever Docker is present.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresParcelRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("parcels")
                    .withUsername("parcels")
                    .withPassword("parcels");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static HikariDataSource dataSource;
    private PostgresParcelRepository repository;

    @BeforeAll
    static void startDataSource() {
        dataSource = DataSourceFactory.create(TestConfig.of(Map.of(
                "url", POSTGRES.getJdbcUrl(),
                "user", POSTGRES.getUsername(),
                "password", POSTGRES.getPassword()
        )));
        DataSourceFactory.applySchema(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void clean() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE TABLE parcels");
        }
        repository = new PostgresParcelRepository(dataSource, MAPPER);
    }

    private Parcel sample(String trackingNumber, Courier courier, ParcelStatus status) {
        return sample(trackingNumber, null, courier, status);
    }

    private Parcel sample(String trackingNumber, String externalId, Courier courier, ParcelStatus status) {
        Address address = new Address("Marszałkowska", "1", "12", "Warszawa", "00-001", "PL");
        GeoLocation geo = new GeoLocation(52.2297, 21.0122, "nominatim", Instant.parse("2026-08-01T10:00:00Z"));
        List<TrackingEvent> events = List.of(
                new TrackingEvent(Instant.parse("2026-08-01T08:00:00Z"), "confirmed", "confirmed", "Warszawa"));
        return new Parcel(UUID.randomUUID(), trackingNumber, externalId, courier, status, address, geo, events,
                Instant.parse("2026-08-01T07:00:00Z"), null);
    }

    @Test
    void insertAndFindById_roundTripsAllFields() {
        Parcel parcel = sample("590123456789012345678901", Courier.INPOST, ParcelStatus.CREATED);
        repository.insert(parcel);

        Optional<Parcel> loaded = repository.findById(parcel.id());

        assertThat(loaded).isPresent();
        Parcel p = loaded.get();
        assertThat(p.trackingNumber()).isEqualTo("590123456789012345678901");
        assertThat(p.courier()).isEqualTo(Courier.INPOST);
        assertThat(p.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(p.deliveryAddress().apartmentNumber()).isEqualTo("12");
        assertThat(p.geoLocation().latitude()).isEqualTo(52.2297);
        assertThat(p.events()).hasSize(1);
        assertThat(p.events().get(0).rawStatus()).isEqualTo("confirmed");
    }

    @Test
    void insert_duplicateTrackingAndCourier_throws() {
        repository.insert(sample("111", Courier.DPD, ParcelStatus.REGISTERED));

        assertThatThrownBy(() -> repository.insert(sample("111", Courier.DPD, ParcelStatus.REGISTERED)))
                .isInstanceOf(DuplicateParcelException.class);
    }

    @Test
    void insert_sameTrackingDifferentCourier_isAllowed() {
        repository.insert(sample("111", Courier.DPD, ParcelStatus.REGISTERED));
        repository.insert(sample("111", Courier.DHL, ParcelStatus.REGISTERED));

        assertThat(repository.findAll(0, 10, null, null)).hasSize(2);
    }

    @Test
    void update_persistsStatusEventsAndGeo() {
        Parcel parcel = sample("222", Courier.INPOST, ParcelStatus.CREATED);
        repository.insert(parcel);

        List<TrackingEvent> newEvents = List.of(
                new TrackingEvent(Instant.parse("2026-08-02T08:00:00Z"), "delivered", "delivered", "Warszawa"));
        Parcel updated = parcel.withRefreshedTracking(ParcelStatus.DELIVERED, newEvents, Instant.parse("2026-08-02T09:00:00Z"));
        repository.update(updated);

        Parcel loaded = repository.findById(parcel.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(loaded.events().get(0).rawStatus()).isEqualTo("delivered");
        assertThat(loaded.lastRefreshedAt()).isEqualTo(Instant.parse("2026-08-02T09:00:00Z"));
    }

    @Test
    void findAll_filtersByStatusAndPaginates() {
        repository.insert(sample("a", Courier.DPD, ParcelStatus.DELIVERED));
        repository.insert(sample("b", Courier.DHL, ParcelStatus.IN_TRANSIT));
        repository.insert(sample("c", Courier.GLS, ParcelStatus.DELIVERED));

        assertThat(repository.findAll(0, 10, ParcelStatus.DELIVERED, null)).hasSize(2);
        assertThat(repository.findAll(0, 10, ParcelStatus.IN_TRANSIT, null)).hasSize(1);
        assertThat(repository.findAll(0, 2, null, null)).hasSize(2);
        assertThat(repository.findAll(1, 2, null, null)).hasSize(1); // second page
    }

    @Test
    void externalId_roundTrips_andFiltersList() {
        repository.insert(sample("a", "ORDER-1", Courier.DPD, ParcelStatus.REGISTERED));
        repository.insert(sample("b", "ORDER-1", Courier.DHL, ParcelStatus.REGISTERED));
        repository.insert(sample("c", "ORDER-2", Courier.GLS, ParcelStatus.REGISTERED));
        repository.insert(sample("d", null, Courier.UPS, ParcelStatus.REGISTERED));

        assertThat(repository.findAll(0, 10, null, "ORDER-1")).hasSize(2);
        assertThat(repository.findAll(0, 10, null, "ORDER-2")).hasSize(1);
        assertThat(repository.findAll(0, 10, null, "NOPE")).isEmpty();

        Parcel one = repository.findAll(0, 10, null, "ORDER-2").get(0);
        assertThat(one.externalId()).isEqualTo("ORDER-2"); // column round-trips

        // Filter composes with status.
        assertThat(repository.findAll(0, 10, ParcelStatus.REGISTERED, "ORDER-1")).hasSize(2);
        assertThat(repository.findAll(0, 10, ParcelStatus.DELIVERED, "ORDER-1")).isEmpty();
    }

    @Test
    void delete_removesRow_andReportsWhetherItExisted() {
        Parcel parcel = sample("333", Courier.UPS, ParcelStatus.REGISTERED);
        repository.insert(parcel);

        assertThat(repository.delete(parcel.id())).isTrue();
        assertThat(repository.findById(parcel.id())).isEmpty();
        assertThat(repository.delete(parcel.id())).isFalse();
    }
}
