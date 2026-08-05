package com.example.parceltracker.adapter.out.courier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregatorProvidersTest {

    @Test
    void resolvesKnownProviders() {
        assertThat(AggregatorProviders.byId("trackingmore")).isInstanceOf(TrackingMoreProvider.class);
        assertThat(AggregatorProviders.byId("17track")).isInstanceOf(SeventeenTrackProvider.class);
        assertThat(AggregatorProviders.byId("track123")).isInstanceOf(Track123Provider.class);
    }

    @Test
    void isCaseInsensitiveAndTrims() {
        assertThat(AggregatorProviders.byId("  17TRACK ")).isInstanceOf(SeventeenTrackProvider.class);
    }

    @Test
    void unknownProvider_failsFastWithKnownList() {
        assertThatThrownBy(() -> AggregatorProviders.byId("17-track"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown aggregator provider")
                .hasMessageContaining("trackingmore");
    }

    @Test
    void idsLists_allShippedProviders() {
        assertThat(AggregatorProviders.ids()).containsExactlyInAnyOrder("trackingmore", "17track", "track123");
    }
}
