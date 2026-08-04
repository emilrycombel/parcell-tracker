package com.example.parceltracker.application.port.out;

import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port for parcel persistence. The application core depends on this interface,
 * never on a concrete database. Implemented by an adapter (e.g. Postgres/JDBC).
 */
public interface ParcelStore {

    /**
     * Persists a new parcel.
     *
     * @throws DuplicateParcelException if a parcel with the same (tracking number, courier)
     *                                  already exists — this is what backs the 409 contract.
     */
    Parcel insert(Parcel parcel);

    Optional<Parcel> findById(UUID id);

    List<Parcel> findAll(int page, int size, ParcelStatus statusFilter);

    /** Persists a status/events/geolocation refresh produced by the application layer. */
    void update(Parcel parcel);

    boolean delete(UUID id);
}
