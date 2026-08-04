package com.example.parceltracker.adapter.in.web.dto;

import com.example.parceltracker.domain.Address;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.GeoLocation;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;
import com.example.parceltracker.domain.TrackingEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wire-format DTOs for the HTTP adapter, kept decoupled from the domain records. */
public final class Dtos {

    private Dtos() {
    }

    public record AddressDto(
            String street,
            String houseNumber,
            String apartmentNumber,
            String city,
            String postalCode,
            String country
    ) {
        public Address toDomain() {
            return new Address(street, houseNumber, apartmentNumber, city, postalCode, country);
        }

        public static AddressDto from(Address a) {
            return new AddressDto(a.street(), a.houseNumber(), a.apartmentNumber(), a.city(), a.postalCode(), a.country());
        }
    }

    public record GeoLocationDto(double latitude, double longitude, String provider, Instant geocodedAt) {
        public static GeoLocationDto from(GeoLocation g) {
            return g == null ? null : new GeoLocationDto(g.latitude(), g.longitude(), g.provider(), g.geocodedAt());
        }
    }

    public record TrackingEventDto(Instant timestamp, String rawStatus, String description, String location) {
        public static TrackingEventDto from(TrackingEvent e) {
            return new TrackingEventDto(e.timestamp(), e.rawStatus(), e.description(), e.location());
        }
    }

    public record RegisterParcelRequest(String trackingNumber, Courier courier, AddressDto deliveryAddress) {
    }

    public record ParcelResponse(
            UUID id,
            String trackingNumber,
            Courier courier,
            ParcelStatus status,
            AddressDto deliveryAddress,
            GeoLocationDto geoLocation,
            List<TrackingEventDto> events,
            Instant createdAt,
            Instant lastRefreshedAt
    ) {
        public static ParcelResponse from(Parcel p) {
            return new ParcelResponse(
                    p.id(),
                    p.trackingNumber(),
                    p.courier(),
                    p.status(),
                    AddressDto.from(p.deliveryAddress()),
                    GeoLocationDto.from(p.geoLocation()),
                    p.events().stream().map(TrackingEventDto::from).toList(),
                    p.createdAt(),
                    p.lastRefreshedAt()
            );
        }
    }

    public record ErrorResponse(String message) {
    }
}
