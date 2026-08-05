package com.example.parceltracker;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the real application.yaml + the ${VAR:default} resolution used at startup. Without the
 * filter these values would be literal "${...}" strings and the service would fail to boot —
 * this test asserts the defaults resolve to usable, correctly-typed values.
 */
class ApplicationConfigTest {

    private Config config() {
        return Config.builder()
                .addFilter(new EnvSubstitutionConfigFilter())
                .build();
    }

    /** Env var value if the runner defines it, otherwise the documented default — mirrors the filter. */
    private static String envOr(String name, String def) {
        String v = System.getenv(name);
        return v != null ? v : def;
    }

    @Test
    void defaultsResolveToUsableValues() {
        Config config = config();

        // Expected values are derived env-or-default so the test is correct whether or not the
        // runner defines these variables — it verifies the filter's contract, not a bare default.
        assertThat(config.get("db.url").asString().get())
                .isEqualTo(envOr("DB_URL", "jdbc:postgresql://localhost:5432/parcels"))
                .doesNotContain("${"); // never a literal placeholder
        assertThat(config.get("db.pool-size").asInt().get())
                .isEqualTo(Integer.parseInt(envOr("DB_POOL_SIZE", "10")));          // hyphenated key
        assertThat(config.get("geocoding.min-interval-ms").asInt().get())
                .isEqualTo(Integer.parseInt(envOr("GEOCODING_MIN_INTERVAL_MS", "1000")));
        assertThat(config.get("courier.refresh.min-interval-seconds").asInt().get())
                .isEqualTo(Integer.parseInt(envOr("REFRESH_MIN_INTERVAL_SECONDS", "300")));
        assertThat(config.get("courier.aggregator.enabled").asBoolean().get())
                .isEqualTo(Boolean.parseBoolean(envOr("AGGREGATOR_ENABLED", "false"))); // deeply nested
        assertThat(config.get("courier.aggregator.api-key").asString().orElse(""))
                .isEqualTo(envOr("AGGREGATOR_API_KEY", ""));
    }

    /**
     * The startup smoke test (ApplicationSmokeIT) relies on an explicit map source overriding
     * application.yaml — this pins that priority so it can't silently regress.
     */
    @Test
    void explicitSourceOverridesYaml() {
        Config config = Config.builder()
                .addFilter(new EnvSubstitutionConfigFilter())
                .addSource(ConfigSources.create(Map.of("db.url", "jdbc:override")))
                .addSource(ConfigSources.classpath("application.yaml"))
                .build();

        assertThat(config.get("db.url").asString().get()).isEqualTo("jdbc:override"); // map wins over yaml
        assertThat(config.get("courier.aggregator.enabled").asBoolean().get())
                .isEqualTo(Boolean.parseBoolean(envOr("AGGREGATOR_ENABLED", "false"))); // yaml (env-or-default) for the rest
    }
}
