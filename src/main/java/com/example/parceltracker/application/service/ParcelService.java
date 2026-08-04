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

import java.time.Instant;
import java.util.List;
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
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ParcelService(ParcelStore store, Geocoder geocoder, CourierGateway courierGateway) {
        this.store = store;
        this.geocoder = geocoder;
        this.courierGateway = courierGateway;
    }

    @Override
    public Parcel register(String trackingNumber, Courier requestedCourier, Address address) {
        Courier courier = (requestedCourier == null || requestedCourier == Courier.UNKNOWN)
                ? courierGateway.detect(trackingNumber)
                : requestedCourier;

        // Geocoding and the initial courier fetch are independent I/O calls — run them
        // concurrently on virtual threads rather than serially.
        Future<Optional<GeoLocation>> geoFuture = virtualExecutor.submit(() -> geocoder.geocode(address));
        Future<CourierTrackingResult> trackingFuture = virtualExecutor.submit(() -> courierGateway.fetchTracking(courier, trackingNumber));

        GeoLocation geoLocation = await(geoFuture).orElse(null);
        CourierTrackingResult tracking = await(trackingFuture);

        Instant now = Instant.now();
        Parcel parcel = new Parcel(
                UUID.randomUUID(),
                trackingNumber,
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

        CourierTrackingResult tracking = courierGateway.fetchTracking(parcel.courier(), parcel.trackingNumber());

        Parcel refreshed = tracking.status() == ParcelStatus.UNKNOWN && tracking.events().isEmpty()
                ? parcel // courier had nothing new — don't overwrite a good status with UNKNOWN
                : parcel.withRefreshedTracking(tracking.status(), mergeEvents(parcel.events(), tracking.events()), Instant.now());

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
    public List<Parcel> list(int page, int size, ParcelStatus statusFilter) {
        return store.findAll(page, size, statusFilter);
    }

    @Override
    public boolean delete(UUID id) {
        return store.delete(id);
    }

    private List<TrackingEvent> mergeEvents(List<TrackingEvent> existing, List<TrackingEvent> fresh) {
        if (fresh.isEmpty()) {
            return existing;
        }
        // Courier feeds are typically full history each time — prefer the freshest full list,
        // but never end up with fewer events than we already had recorded.
        return fresh.size() >= existing.size() ? fresh : existing;
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Async task failed", e);
        }
    }
}
