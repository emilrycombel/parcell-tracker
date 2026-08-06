package com.example.parceltracker.adapter.out.geocoding;

import com.example.parceltracker.application.port.out.Geocoder;
import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.GeoLocation;

import java.util.Optional;

/**
 * Fail-safe {@link Geocoder} used when no geocoding endpoint is configured. Always returns
 * empty, so parcels register without coordinates. Wired by the composition root when
 * {@code GEOCODING_BASE_URL} is unset — geocoding is opt-in (OSM policy forbids
 * package/vehicle-tracking services from using the public Nominatim instance).
 */
public final class DisabledGeocoder implements Geocoder {

    @Override
    public Optional<GeoLocation> geocode(Address address) {
        return Optional.empty();
    }
}
