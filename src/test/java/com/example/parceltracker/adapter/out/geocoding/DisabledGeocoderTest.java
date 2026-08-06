package com.example.parceltracker.adapter.out.geocoding;

import com.example.parceltracker.domain.Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisabledGeocoderTest {

    @Test
    void alwaysReturnsEmpty() {
        Address address = new Address("Marszałkowska", "1", null, "Warszawa", "00-001", "PL");
        assertThat(new DisabledGeocoder().geocode(address)).isEmpty();
    }
}
