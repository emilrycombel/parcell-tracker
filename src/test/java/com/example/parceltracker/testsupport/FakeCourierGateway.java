package com.example.parceltracker.testsupport;

import com.example.parceltracker.application.port.out.CourierGateway;
import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Courier;

import java.util.concurrent.atomic.AtomicInteger;

/** Configurable {@link CourierGateway} fake: preset detect + fetch results, counts fetches. */
public final class FakeCourierGateway implements CourierGateway {

    private final AtomicInteger fetchCalls = new AtomicInteger();
    private volatile Courier detected = Courier.UNKNOWN;
    private volatile CourierTrackingResult fetchResult = CourierTrackingResult.unknown();

    public FakeCourierGateway detecting(Courier courier) {
        this.detected = courier;
        return this;
    }

    public FakeCourierGateway fetching(CourierTrackingResult result) {
        this.fetchResult = result;
        return this;
    }

    @Override
    public Courier detect(String trackingNumber) {
        return detected;
    }

    @Override
    public CourierTrackingResult fetchTracking(Courier courier, String trackingNumber) {
        fetchCalls.incrementAndGet();
        return fetchResult;
    }

    public int fetchCalls() {
        return fetchCalls.get();
    }
}
