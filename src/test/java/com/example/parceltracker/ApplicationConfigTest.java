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

    @Test
    void defaultsResolveToUsableValues() {
        Config config = config();

        assertThat(config.get("db.url").asString().get())
                .isEqualTo("jdbc:postgresql://localhost:5432/parcels")
                .doesNotContain("${"); // not a literal placeholder
        assertThat(config.get("db.pool-size").asInt().get()).isEqualTo(10);       // hyphenated key
        assertThat(config.get("geocoding.min-interval-ms").asInt().get()).isEqualTo(1000);
        assertThat(config.get("courier.refresh.min-interval-seconds").asInt().get()).isEqualTo(300);
        assertThat(config.get("courier.aggregator.enabled").asBoolean().get()).isFalse(); // deeply nested
        assertThat(config.get("courier.aggregator.api-key").asString().orElse("")).isEmpty();
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

        assertThat(config.get("db.url").asString().get()).isEqualTo("jdbc:override"); // map wins
        assertThat(config.get("courier.aggregator.enabled").asBoolean().get()).isFalse(); // yaml for the rest
    }
}
