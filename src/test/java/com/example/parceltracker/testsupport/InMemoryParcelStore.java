package com.example.parceltracker.testsupport;

import com.example.parceltracker.application.port.out.DuplicateParcelException;
import com.example.parceltracker.application.port.out.ParcelStore;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link ParcelStore} for application-layer tests. Mirrors the real adapter's
 * observable contract — including the (tracking number, courier) uniqueness that backs the
 * 409 — so {@code ParcelService} can be tested with zero infrastructure.
 */
public final class InMemoryParcelStore implements ParcelStore {

    private final Map<UUID, Parcel> byId = new LinkedHashMap<>();

    @Override
    public Parcel insert(Parcel parcel) {
        boolean duplicate = byId.values().stream().anyMatch(p ->
                p.trackingNumber().equals(parcel.trackingNumber()) && p.courier() == parcel.courier());
        if (duplicate) {
            throw new DuplicateParcelException(parcel.trackingNumber(), parcel.courier(), null);
        }
        byId.put(parcel.id(), parcel);
        return parcel;
    }

    @Override
    public Optional<Parcel> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Parcel> findAll(int page, int size, ParcelStatus statusFilter) {
        return byId.values().stream()
                .filter(p -> statusFilter == null || p.status() == statusFilter)
                .sorted(Comparator.comparing(Parcel::createdAt).reversed())
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    @Override
    public void update(Parcel parcel) {
        byId.put(parcel.id(), parcel);
    }

    @Override
    public boolean delete(UUID id) {
        return byId.remove(id) != null;
    }

    public int size() {
        return byId.size();
    }
}
