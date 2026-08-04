package com.example.parceltracker.service;

import com.example.parceltracker.courier.CourierRouter;
import com.example.parceltracker.courier.CourierTrackingResult;
import com.example.parceltracker.db.ParcelRepository;
import com.example.parceltracker.geocoding.GeocodingService;
import com.example.parceltracker.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ParcelService {

    private final ParcelRepository repository;
    private final GeocodingService geocodingService;
    private final CourierRouter courierRouter;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ParcelService(ParcelRepository repository, GeocodingService geocodingService, CourierRouter courierRouter) {
        this.repository = repository;
        this.geocodingService = geocodingService;
        this.courierRouter = courierRouter;
    }

    public Parcel register(String trackingNumber, Courier requestedCourier, Address address) {
        Courier courier = (requestedCourier == null || requestedCourier == Courier.UNKNOWN)
                ? courierRouter.detect(trackingNumber)
                : requestedCourier;

        // Geocoding and the initial courier fetch are independent I/O calls — run them
        // concurrently on virtual threads rather than serially.
        Future<Optional<GeoLocation>> geoFuture = virtualExecutor.submit(() -> geocodingService.geocode(address));
        Future<CourierTrackingResult> trackingFuture = virtualExecutor.submit(() -> courierRouter.fetchTracking(courier, trackingNumber));

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

        return repository.insert(parcel);
    }

    /** Loads the parcel, re-fetches its status from the courier, persists, and returns the fresh view. */
    public Optional<Parcel> getRefreshed(UUID id) {
        Optional<Parcel> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Parcel parcel = existing.get();

        CourierTrackingResult tracking = courierRouter.fetchTracking(parcel.courier(), parcel.trackingNumber());

        Parcel refreshed = tracking.status() == ParcelStatus.UNKNOWN && tracking.events().isEmpty()
                ? parcel // courier had nothing new — don't overwrite a good status with UNKNOWN
                : parcel.withRefreshedTracking(tracking.status(), mergeEvents(parcel.events(), tracking.events()), Instant.now());

        // Backfill geocoding if it failed at registration time.
        if (refreshed.geoLocation() == null) {
            refreshed = geocodingService.geocode(refreshed.deliveryAddress())
                    .map(refreshed::withGeoLocation)
                    .orElse(refreshed);
        }

        repository.update(refreshed);
        return Optional.of(refreshed);
    }

    public List<Parcel> list(int page, int size, ParcelStatus statusFilter) {
        return repository.findAll(page, size, statusFilter);
    }

    public boolean delete(UUID id) {
        return repository.delete(id);
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
