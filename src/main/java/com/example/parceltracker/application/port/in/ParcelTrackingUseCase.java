package com.example.parceltracker.application.port.in;

import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driving port: the operations the outside world can invoke on the parcel-tracking core.
 * Driving adapters (e.g. the HTTP endpoint) depend on this interface, not on the concrete
 * service. Kept as one cohesive port for this single feature rather than one interface per
 * method — see design.md for the rationale.
 */
public interface ParcelTrackingUseCase {

    /**
     * Registers a parcel: detects the courier if not given, geocodes the address and fetches
     * the initial courier status concurrently, then persists.
     *
     * @throws com.example.parceltracker.application.port.out.DuplicateParcelException
     *         if this (tracking number, courier) is already registered.
     */
    Parcel register(String trackingNumber, Courier requestedCourier, Address address);

    /** Loads a parcel, refreshes its status from the courier, persists, and returns the fresh view. */
    Optional<Parcel> getRefreshed(UUID id);

    /** Returns a page of parcels without triggering a per-item courier refresh. */
    List<Parcel> list(int page, int size, ParcelStatus statusFilter);

    boolean delete(UUID id);
}
