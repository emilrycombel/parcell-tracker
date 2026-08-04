package com.example.parceltracker.testsupport;

import com.example.parceltracker.application.port.out.Geocoder;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.GeoLocation;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Configurable {@link Geocoder} fake: returns a preset result and counts invocations. */
public final class FakeGeocoder implements Geocoder {

    private final AtomicInteger calls = new AtomicInteger();
    private volatile Optional<GeoLocation> result;

    public FakeGeocoder(Optional<GeoLocation> result) {
        this.result = result;
    }

    public static FakeGeocoder returning(GeoLocation location) {
        return new FakeGeocoder(Optional.of(location));
    }

    public static FakeGeocoder failing() {
        return new FakeGeocoder(Optional.empty());
    }

    public void setResult(Optional<GeoLocation> result) {
        this.result = result;
    }

    @Override
    public Optional<GeoLocation> geocode(Address address) {
        calls.incrementAndGet();
        return result;
    }

    public int calls() {
        return calls.get();
    }
}
