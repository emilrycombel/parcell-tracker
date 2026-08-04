package com.example.parceltracker.application.service;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;
import com.example.parceltracker.testsupport.FakeCourierGateway;
import com.example.parceltracker.testsupport.FakeGeocoder;
import com.example.parceltracker.testsupport.InMemoryParcelStore;
import com.example.parceltracker.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Refresh-on-read throttle: within the window, a GET must not call the courier. */
class ParcelServiceRefreshThrottleTest {

    private static final Address ADDRESS =
            new Address("Marszałkowska", "1", null, "Warszawa", "00-001", "PL");
    private static final Instant T0 = Instant.parse("2026-08-04T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private InMemoryParcelStore store;
    private FakeCourierGateway courier;
    private MutableClock clock;
    private ParcelService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryParcelStore();
        courier = new FakeCourierGateway();
        clock = new MutableClock(T0);
        GeoLocation geo = new GeoLocation(52.2, 21.0, "nominatim", T0);
        service = new ParcelService(store, FakeGeocoder.returning(geo), courier, WINDOW, clock);
    }

    private Parcel registerWithEvents() {
        TrackingEvent event = new TrackingEvent(T0.minusSeconds(3600), "in_transit", "in transit", "Warszawa");
        courier.fetching(new CourierTrackingResult(ParcelStatus.IN_TRANSIT, List.of(event)));
        return service.register("590123456789012345678901", Courier.INPOST, ADDRESS);
    }

    @Test
    void withinWindow_doesNotCallCourier() {
        Parcel registered = registerWithEvents();
        int fetchesAfterRegister = courier.fetchCalls(); // == 1

        clock.advance(Duration.ofMinutes(2)); // still inside the 5-minute window
        Optional<Parcel> result = service.getRefreshed(registered.id());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(courier.fetchCalls()).isEqualTo(fetchesAfterRegister); // no extra courier call
    }

    @Test
    void afterWindow_callsCourierAgain() {
        Parcel registered = registerWithEvents();
        int fetchesAfterRegister = courier.fetchCalls();

        courier.fetching(new CourierTrackingResult(ParcelStatus.DELIVERED, List.of(
                new TrackingEvent(T0.plusSeconds(60), "delivered", "delivered", "Warszawa"))));
        clock.advance(Duration.ofMinutes(6)); // past the window
        Parcel refreshed = service.getRefreshed(registered.id()).orElseThrow();

        assertThat(courier.fetchCalls()).isEqualTo(fetchesAfterRegister + 1);
        assertThat(refreshed.status()).isEqualTo(ParcelStatus.DELIVERED);
    }

    @Test
    void zeroInterval_alwaysRefreshes() {
        ParcelService noThrottle = new ParcelService(store,
                FakeGeocoder.failing(), courier, Duration.ZERO, clock);
        courier.fetching(CourierTrackingResult.unknown());
        Parcel registered = noThrottle.register("111", Courier.DPD, ADDRESS);
        int calls = courier.fetchCalls();

        noThrottle.getRefreshed(registered.id());

        assertThat(courier.fetchCalls()).isEqualTo(calls + 1);
    }
}
