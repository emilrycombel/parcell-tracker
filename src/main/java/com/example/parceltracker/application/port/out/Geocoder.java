package com.example.parceltracker.application.port.out;

import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.GeoLocation;

import java.util.Optional;

/**
 * Driven port for turning an address into coordinates. Fail-soft by contract: an
 * implementation returns {@link Optional#empty()} rather than throwing, so geocoding
 * problems never block registration or refresh.
 */
public interface Geocoder {

    Optional<GeoLocation> geocode(Address address);
}
