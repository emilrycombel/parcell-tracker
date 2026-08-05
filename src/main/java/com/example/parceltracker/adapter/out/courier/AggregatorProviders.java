package com.example.parceltracker.adapter.out.courier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registry of the aggregator integrations that ship with the service. */
public final class AggregatorProviders {

    private static final Map<String, AggregatorProvider> BY_ID = new LinkedHashMap<>();

    static {
        register(new TrackingMoreProvider());
        register(new SeventeenTrackProvider());
        register(new Track123Provider());
    }

    private AggregatorProviders() {
    }

    private static void register(AggregatorProvider provider) {
        BY_ID.put(provider.id(), provider);
    }

    /** Resolves a provider by id (case-insensitive). Fails fast on an unknown id (misconfig). */
    public static AggregatorProvider byId(String id) {
        AggregatorProvider provider = id == null ? null : BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unknown aggregator provider: '" + id + "'. Known providers: " + BY_ID.keySet());
        }
        return provider;
    }

    public static Set<String> ids() {
        return Set.copyOf(BY_ID.keySet());
    }
}
