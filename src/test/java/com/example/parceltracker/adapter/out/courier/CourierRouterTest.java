package com.example.parceltracker.adapter.out.courier;

import com.example.parceltracker.application.port.out.CourierTrackingResult;
import com.example.parceltracker.domain.Courier;
import com.example.parceltracker.domain.ParcelStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourierRouterTest {

    /** A stub courier client that claims a fixed courier and returns a canned result. */
    private static CourierClient client(Courier claims, int priority, CourierTrackingResult result) {
        return new CourierClient() {
            @Override
            public boolean supports(Courier courier, String trackingNumber) {
                return courier == claims;
            }

            @Override
            public CourierTrackingResult fetchTracking(String trackingNumber) {
                return result;
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }

    @Test
    void detect_recognizesInPostNumberFormat() {
        CourierRouter router = new CourierRouter(List.of());
        assertThat(router.detect("590123456789012345678901")).isEqualTo(Courier.INPOST);
    }

    @Test
    void detect_returnsUnknown_forNonInPostFormat() {
        CourierRouter router = new CourierRouter(List.of());
        assertThat(router.detect("1Z999AA10123456784")).isEqualTo(Courier.UNKNOWN);
        assertThat(router.detect("123")).isEqualTo(Courier.UNKNOWN); // too short
    }

    @Test
    void fetchTracking_dispatchesToSupportingClient() {
        CourierTrackingResult inpost = new CourierTrackingResult(ParcelStatus.DELIVERED, List.of());
        CourierRouter router = new CourierRouter(List.of(
                client(Courier.INPOST, 0, inpost),
                client(Courier.DPD, 100, CourierTrackingResult.unknown())
        ));

        assertThat(router.fetchTracking(Courier.INPOST, "x").status()).isEqualTo(ParcelStatus.DELIVERED);
    }

    @Test
    void fetchTracking_honorsPriorityOrder_whenMultipleSupport() {
        CourierTrackingResult free = new CourierTrackingResult(ParcelStatus.IN_TRANSIT, List.of());
        CourierTrackingResult paid = new CourierTrackingResult(ParcelStatus.EXCEPTION, List.of());
        // Both claim INPOST; the lower-priority (0) client must win regardless of list order.
        CourierRouter router = new CourierRouter(List.of(
                client(Courier.INPOST, 100, paid),
                client(Courier.INPOST, 0, free)
        ));

        assertThat(router.fetchTracking(Courier.INPOST, "x").status()).isEqualTo(ParcelStatus.IN_TRANSIT);
    }

    @Test
    void fetchTracking_noSupportingClient_returnsUnknown() {
        CourierRouter router = new CourierRouter(List.of(
                client(Courier.INPOST, 0, new CourierTrackingResult(ParcelStatus.DELIVERED, List.of()))
        ));

        assertThat(router.fetchTracking(Courier.DHL, "x").status()).isEqualTo(ParcelStatus.UNKNOWN);
    }
}
