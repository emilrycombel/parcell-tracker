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

    /**
     * Returns a page of parcels, optionally narrowed by status and/or external id. A null
     * filter means "don't filter on that dimension"; the two compose.
     */
    List<Parcel> findAll(int page, int size, ParcelStatus statusFilter, String externalIdFilter);

    /**
     * Persists a refresh with optimistic concurrency: the update applies only if the stored row
     * still has {@code parcel.version()} (i.e. no other refresh committed in between), and bumps
     * the version on success.
     *
     * @return {@code true} if persisted; {@code false} if a concurrent update won the race (the
     *         caller should reload and retry) or the row no longer exists.
     */
    boolean update(Parcel parcel);

    boolean delete(UUID id);
}
