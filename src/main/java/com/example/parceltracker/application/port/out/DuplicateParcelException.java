package com.example.parceltracker.application.port.out;

import com.example.parceltracker.domain.Courier;

/**
 * Raised by a {@link ParcelStore} when a parcel with the same (tracking number, courier)
 * is already registered. Part of the persistence port's contract so the driving side can
 * translate it (e.g. to HTTP 409) without knowing the storage technology.
 */
public final class DuplicateParcelException extends RuntimeException {

    public DuplicateParcelException(String trackingNumber, Courier courier, Throwable cause) {
        super("Parcel already registered: " + trackingNumber + " / " + courier, cause);
    }
}
