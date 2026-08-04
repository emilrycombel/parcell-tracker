package com.example.parceltracker.testsupport;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import java.util.Map;

/** Builds an isolated Helidon {@link Config} from a flat map — no env/system/file sources. */
public final class TestConfig {

    private TestConfig() {
    }

    public static Config of(Map<String, String> values) {
        return Config.just(ConfigSources.create(values));
    }
}
