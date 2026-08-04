package com.example.parceltracker.application.service;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.application.port.out.DuplicateParcelException;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;
import com.example.parceltracker.testsupport.FakeCourierGateway;
import com.example.parceltracker.testsupport.FakeGeocoder;
import com.example.parceltracker.testsupport.InMemoryParcelStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Application-core tests: {@code ParcelService} exercised purely through in-memory fakes at
 * its ports. No HTTP, no database — the ports are what make this possible.
 */
class ParcelServiceTest {

    private static final Address ADDRESS =
            new Address("Marszałkowska", "1", null, "Warszawa", "00-001", "PL");
    private static final GeoLocation GEO =
            new GeoLocation(52.2297, 21.0122, "nominatim", Instant.parse("2026-08-01T10:00:00Z"));

    private InMemoryParcelStore store;
    private FakeGeocoder geocoder;
    private FakeCourierGateway courier;
    private ParcelService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryParcelStore();
        geocoder = FakeGeocoder.returning(GEO);
        courier = new FakeCourierGateway();
        service = new ParcelService(store, geocoder, courier);
    }

    @Test
    void register_detectsCourier_whenNoneRequested() {
        courier.detecting(Courier.INPOST).fetching(CourierTrackingResult.unknown());

        Parcel parcel = service.register("590123456789012345678901", null, ADDRESS);

        assertThat(parcel.courier()).isEqualTo(Courier.INPOST);
        assertThat(store.findById(parcel.id())).isPresent();
    }

    @Test
    void register_honorsRequestedCourier_overDetection() {
        courier.detecting(Courier.INPOST);

        Parcel parcel = service.register("XYZ", Courier.DPD, ADDRESS);

        assertThat(parcel.courier()).isEqualTo(Courier.DPD);
    }

    @Test
    void register_unknownCourierStatus_becomesRegistered() {
        courier.fetching(CourierTrackingResult.unknown());

        Parcel parcel = service.register("123", Courier.DPD, ADDRESS);

        assertThat(parcel.status()).isEqualTo(ParcelStatus.REGISTERED);
        assertThat(parcel.lastRefreshedAt()).isNull(); // no events yet
    }

    @Test
    void register_appliesCourierStatusAndEvents() {
        TrackingEvent event = new TrackingEvent(Instant.parse("2026-08-01T09:00:00Z"), "in_transit", "in transit", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.IN_TRANSIT, List.of(event)));

        Parcel parcel = service.register("590123456789012345678901", Courier.INPOST, ADDRESS);

        assertThat(parcel.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(parcel.events()).containsExactly(event);
        assertThat(parcel.lastRefreshedAt()).isNotNull();
        assertThat(parcel.geoLocation()).isEqualTo(GEO);
    }

    @Test
    void register_geocodingFailure_stillRegistersWithoutLocation() {
        geocoder.setResult(Optional.empty());
        courier.fetching(CourierTrackingResult.unknown());

        Parcel parcel = service.register("123", Courier.DPD, ADDRESS);

        assertThat(parcel.geoLocation()).isNull();
        assertThat(store.findById(parcel.id())).isPresent();
    }

    @Test
    void register_duplicateTrackingAndCourier_throws() {
        courier.fetching(CourierTrackingResult.unknown());
        service.register("590123456789012345678901", Courier.INPOST, ADDRESS);

        assertThatThrownBy(() ->
                service.register("590123456789012345678901", Courier.INPOST, ADDRESS))
                .isInstanceOf(DuplicateParcelException.class);
    }

    @Test
    void getRefreshed_missing_returnsEmpty() {
        assertThat(service.getRefreshed(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getRefreshed_appliesNewStatusAndPersists() {
        courier.fetching(CourierTrackingResult.unknown());
        Parcel registered = service.register("123", Courier.DPD, ADDRESS);

        TrackingEvent event = new TrackingEvent(Instant.parse("2026-08-02T09:00:00Z"), "delivered", "delivered", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.DELIVERED, List.of(event)));

        Parcel refreshed = service.getRefreshed(registered.id()).orElseThrow();

        assertThat(refreshed.status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(refreshed.lastRefreshedAt()).isNotNull();
        assertThat(store.findById(registered.id()).orElseThrow().status()).isEqualTo(ParcelStatus.DELIVERED);
    }

    @Test
    void getRefreshed_courierReturnsNothing_keepsPriorGoodStatus() {
        TrackingEvent event = new TrackingEvent(Instant.parse("2026-08-01T09:00:00Z"), "in_transit", "in transit", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.IN_TRANSIT, List.of(event)));
        Parcel registered = service.register("590123456789012345678901", Courier.INPOST, ADDRESS);

        courier.fetching(CourierTrackingResult.unknown()); // nothing new

        Parcel refreshed = service.getRefreshed(registered.id()).orElseThrow();

        assertThat(refreshed.status()).isEqualTo(ParcelStatus.IN_TRANSIT); // not overwritten with UNKNOWN
        assertThat(refreshed.events()).containsExactly(event);
    }

    @Test
    void getRefreshed_mergesEvents_unionDedupedAndSorted() {
        TrackingEvent e1 = new TrackingEvent(Instant.parse("2026-08-01T08:00:00Z"), "confirmed", "confirmed", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.CREATED, List.of(e1)));
        Parcel registered = service.register("590123456789012345678901", Courier.INPOST, ADDRESS);

        // Fresh feed repeats e1 (dedupe) and adds an earlier + a later event (ordering).
        TrackingEvent earlier = new TrackingEvent(Instant.parse("2026-08-01T06:00:00Z"), "created", "created", "Kraków");
        TrackingEvent later = new TrackingEvent(Instant.parse("2026-08-02T09:00:00Z"), "delivered", "delivered", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.DELIVERED, List.of(e1, later, earlier)));

        Parcel refreshed = service.getRefreshed(registered.id()).orElseThrow();

        assertThat(refreshed.events())
                .extracting(TrackingEvent::rawStatus)
                .containsExactly("created", "confirmed", "delivered"); // deduped, sorted by time
    }

    @Test
    void getRefreshed_shorterFreshFeed_neverLosesExistingEvents() {
        TrackingEvent a = new TrackingEvent(Instant.parse("2026-08-01T08:00:00Z"), "confirmed", "confirmed", "Warszawa");
        TrackingEvent b = new TrackingEvent(Instant.parse("2026-08-01T12:00:00Z"), "in_transit", "in transit", "Łódź");
        courier.fetching(new CourierTrackingResult(ParcelStatus.IN_TRANSIT, List.of(a, b)));
        Parcel registered = service.register("590123456789012345678901", Courier.INPOST, ADDRESS);

        // Courier now returns a single (different) event — must not drop a and b.
        TrackingEvent c = new TrackingEvent(Instant.parse("2026-08-02T09:00:00Z"), "delivered", "delivered", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.DELIVERED, List.of(c)));

        Parcel refreshed = service.getRefreshed(registered.id()).orElseThrow();

        assertThat(refreshed.events()).extracting(TrackingEvent::rawStatus)
                .containsExactly("confirmed", "in_transit", "delivered");
    }

    @Test
    void getRefreshed_backfillsGeocoding_whenMissing() {
        geocoder.setResult(Optional.empty()); // fails at registration
        courier.fetching(CourierTrackingResult.unknown());
        Parcel registered = service.register("123", Courier.DPD, ADDRESS);
        assertThat(registered.geoLocation()).isNull();

        geocoder.setResult(Optional.of(GEO)); // now succeeds

        Parcel refreshed = service.getRefreshed(registered.id()).orElseThrow();

        assertThat(refreshed.geoLocation()).isEqualTo(GEO);
    }

    @Test
    void list_and_delete_delegateToStore() {
        courier.fetching(CourierTrackingResult.unknown());
        Parcel a = service.register("111", Courier.DPD, ADDRESS);
        service.register("222", Courier.DHL, ADDRESS);

        assertThat(service.list(0, 10, null)).hasSize(2);
        assertThat(service.delete(a.id())).isTrue();
        assertThat(service.list(0, 10, null)).hasSize(1);
        assertThat(service.delete(a.id())).isFalse();
    }

    @Test
    void list_filtersByStatus() {
        courier.fetching(new CourierTrackingResult(ParcelStatus.DELIVERED, List.of()));
        service.register("111", Courier.DPD, ADDRESS);
        courier.fetching(CourierTrackingResult.unknown());
        service.register("222", Courier.DHL, ADDRESS); // REGISTERED

        assertThat(service.list(0, 10, ParcelStatus.DELIVERED)).hasSize(1);
        assertThat(service.list(0, 10, ParcelStatus.REGISTERED)).hasSize(1);
    }
}
