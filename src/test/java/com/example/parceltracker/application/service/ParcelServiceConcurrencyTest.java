package com.example.parceltracker.application.service;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.application.port.out.ParcelStore;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;
import com.example.parceltracker.testsupport.FakeCourierGateway;
import com.example.parceltracker.testsupport.FakeGeocoder;
import com.example.parceltracker.testsupport.InMemoryParcelStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Optimistic-locking behavior of refresh-on-read (T14). */
class ParcelServiceConcurrencyTest {

    private static final Address ADDRESS =
            new Address("Marszałkowska", "1", null, "Warszawa", "00-001", "PL");
    private static final GeoLocation GEO =
            new GeoLocation(52.2, 21.0, "nominatim", Instant.parse("2026-08-01T10:00:00Z"));

    /** Wraps an in-memory store and injects one concurrent write just before the first update. */
    private static final class ConflictingStore implements ParcelStore {
        final InMemoryParcelStore delegate = new InMemoryParcelStore();
        Runnable concurrentWriterOnFirstUpdate;
        private boolean fired;

        @Override public Parcel insert(Parcel p) { return delegate.insert(p); }
        @Override public Optional<Parcel> findById(UUID id) { return delegate.findById(id); }
        @Override public List<Parcel> findAll(int page, int size, ParcelStatus s, String e) { return delegate.findAll(page, size, s, e); }
        @Override public boolean delete(UUID id) { return delegate.delete(id); }

        @Override public boolean update(Parcel p) {
            if (!fired && concurrentWriterOnFirstUpdate != null) {
                fired = true;
                concurrentWriterOnFirstUpdate.run(); // commits a competing refresh, bumping the version
            }
            return delegate.update(p);
        }
    }

    @Test
    void getRefreshed_onVersionConflict_reloadsAndUnionMergesEvents() {
        ConflictingStore store = new ConflictingStore();
        FakeCourierGateway courier = new FakeCourierGateway();
        ParcelService service = new ParcelService(store, FakeGeocoder.returning(GEO), courier);

        TrackingEvent e1 = new TrackingEvent(Instant.parse("2026-08-01T08:00:00Z"), "confirmed", "confirmed", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.CREATED, List.of(e1)));
        Parcel registered = service.register("590123456789012345678901", Courier.INPOST, null, ADDRESS);
        UUID id = registered.id();

        // Our refresh will fetch e2 (a later event).
        TrackingEvent e2 = new TrackingEvent(Instant.parse("2026-08-02T09:00:00Z"), "delivered", "delivered", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.DELIVERED, List.of(e1, e2)));

        // A parallel refresh commits e3 at version 0 (→ 1) right before our first update lands.
        TrackingEvent e3 = new TrackingEvent(Instant.parse("2026-08-01T20:00:00Z"), "in_transit", "in transit", "Łódź");
        store.concurrentWriterOnFirstUpdate = () -> {
            Parcel current = store.delegate.findById(id).orElseThrow(); // version 0
            boolean ok = store.delegate.update(current.withRefreshedTracking(
                    ParcelStatus.IN_TRANSIT, List.of(e1, e3), Instant.parse("2026-08-01T20:05:00Z")));
            assertThat(ok).isTrue();
        };

        Parcel result = service.getRefreshed(id).orElseThrow();

        // Our e2 survives the conflict — it is union-merged onto the concurrent [e1, e3], sorted by time.
        assertThat(result.events()).extracting(TrackingEvent::rawStatus)
                .containsExactly("confirmed", "in_transit", "delivered");
        assertThat(result.status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(store.delegate.findById(id).orElseThrow().events()).hasSize(3); // persisted
    }
}
