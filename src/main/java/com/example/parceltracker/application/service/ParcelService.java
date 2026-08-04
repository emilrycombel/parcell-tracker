package com.example.parceltracker.application.service;

import com.example.parceltracker.application.port.in.ParcelTrackingUseCase;
import com.example.parceltracker.application.port.out.CourierGateway;
import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.application.port.out.Geocoder;
import com.example.parceltracker.application.port.out.ParcelStore;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The application core. Orchestrates the driven ports ({@link ParcelStore},
 * {@link Geocoder}, {@link CourierGateway}) to satisfy the {@link ParcelTrackingUseCase}
 * driving port. Has no knowledge of HTTP, JDBC, or any specific courier/geocoder — those
 * live behind the ports, which makes this class unit-testable with in-memory fakes.
 */
public final class ParcelService implements ParcelTrackingUseCase {

    private final ParcelStore store;
    private final Geocoder geocoder;
    private final CourierGateway courierGateway;
    /** Minimum time between courier refreshes for one parcel; {@link Duration#ZERO} = always refresh. */
    private final Duration minRefreshInterval;
    private final Clock clock;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Convenience for tests/callers that don't throttle: always refreshes on read. */
    public ParcelService(ParcelStore store, Geocoder geocoder, CourierGateway courierGateway) {
        this(store, geocoder, courierGateway, Duration.ZERO, Clock.systemUTC());
    }

    public ParcelService(ParcelStore store, Geocoder geocoder, CourierGateway courierGateway,
                         Duration minRefreshInterval, Clock clock) {
        this.store = store;
        this.geocoder = geocoder;
        this.courierGateway = courierGateway;
        this.minRefreshInterval = minRefreshInterval;
        this.clock = clock;
    }

    @Override
    public Parcel register(String trackingNumber, Courier requestedCourier, String externalId, Address address) {
        Courier courier = (requestedCourier == null || requestedCourier == Courier.UNKNOWN)
                ? courierGateway.detect(trackingNumber)
                : requestedCourier;

        // Geocoding and the initial courier fetch are independent I/O calls — run them
        // concurrently on virtual threads rather than serially.
        Future<Optional<GeoLocation>> geoFuture = virtualExecutor.submit(() -> geocoder.geocode(address));
        Future<CourierTrackingResult> trackingFuture = virtualExecutor.submit(() -> courierGateway.fetchTracking(courier, trackingNumber));

        GeoLocation geoLocation = await(geoFuture).orElse(null);
        CourierTrackingResult tracking = await(trackingFuture);

        Instant now = clock.instant();
        Parcel parcel = new Parcel(
                UUID.randomUUID(),
                trackingNumber,
                externalId,
                courier,
                tracking.status() == ParcelStatus.UNKNOWN ? ParcelStatus.REGISTERED : tracking.status(),
                address,
                geoLocation,
                tracking.events(),
                now,
                tracking.events().isEmpty() ? null : now
        );

        return store.insert(parcel);
    }

    @Override
    public Optional<Parcel> getRefreshed(UUID id) {
        Optional<Parcel> existing = store.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Parcel parcel = existing.get();

        Instant now = clock.instant();
        if (withinRefreshWindow(parcel, now)) {
            // Refreshed recently enough — serve the stored state without hitting the courier,
            // so hot reads of a single parcel don't hammer the courier API or run up cost.
            return Optional.of(parcel);
        }

        CourierTrackingResult tracking = courierGateway.fetchTracking(parcel.courier(), parcel.trackingNumber());

        Parcel refreshed = tracking.status() == ParcelStatus.UNKNOWN && tracking.events().isEmpty()
                ? parcel // courier had nothing new — don't overwrite a good status with UNKNOWN
                : parcel.withRefreshedTracking(tracking.status(), mergeEvents(parcel.events(), tracking.events()), now);

        // Backfill geocoding if it failed at registration time.
        if (refreshed.geoLocation() == null) {
            refreshed = geocoder.geocode(refreshed.deliveryAddress())
                    .map(refreshed::withGeoLocation)
                    .orElse(refreshed);
        }

        store.update(refreshed);
        return Optional.of(refreshed);
    }

    @Override
    public List<Parcel> list(int page, int size, ParcelStatus statusFilter, String externalId) {
        return store.findAll(page, size, statusFilter, externalId);
    }

    @Override
    public boolean delete(UUID id) {
        return store.delete(id);
    }

    private boolean withinRefreshWindow(Parcel parcel, Instant now) {
        return !minRefreshInterval.isZero()
                && parcel.lastRefreshedAt() != null
                && Duration.between(parcel.lastRefreshedAt(), now).compareTo(minRefreshInterval) < 0;
    }

    /**
     * Merges the freshly fetched events with what we already had, keyed by (timestamp,
     * rawStatus): a union that dedupes and stays sorted by time. Unlike a size comparison,
     * this never loses events when a courier reorders its feed or returns the same count with
     * changed content.
     */
    private List<TrackingEvent> mergeEvents(List<TrackingEvent> existing, List<TrackingEvent> fresh) {
        if (fresh.isEmpty()) {
            return existing;
        }
        Map<String, TrackingEvent> byKey = new LinkedHashMap<>();
        for (TrackingEvent e : existing) {
            byKey.put(eventKey(e), e);
        }
        for (TrackingEvent e : fresh) {
            byKey.put(eventKey(e), e); // fresh wins on a key collision
        }
        List<TrackingEvent> merged = new ArrayList<>(byKey.values());
        merged.sort(Comparator.comparing(TrackingEvent::timestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(merged);
    }

    private static String eventKey(TrackingEvent e) {
        String ts = e.timestamp() == null ? "" : e.timestamp().toString();
        String raw = e.rawStatus() == null ? "" : e.rawStatus();
        return ts + '|' + raw;
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Async task failed", e);
        }
    }
}
