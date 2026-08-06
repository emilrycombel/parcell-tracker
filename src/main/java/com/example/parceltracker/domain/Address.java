package com.example.parceltracker.domain;

public record Address(
        String street,
        String houseNumber,
        String apartmentNumber,
        String city,
        String postalCode,
        String country
) {
    public Address {
        if (country == null || country.isBlank()) {
            country = "PL";
        }
    }

    /** Single-line form, good enough for a geocoder query. */
    public String toQueryString() {
        StringBuilder sb = new StringBuilder();
        sb.append(street).append(' ').append(houseNumber);
        if (apartmentNumber != null && !apartmentNumber.isBlank()) {
            sb.append('/').append(apartmentNumber);
        }
        sb.append(", ").append(postalCode).append(' ').append(city);
        sb.append(", ").append(country);
        return sb.toString();
    }
}
