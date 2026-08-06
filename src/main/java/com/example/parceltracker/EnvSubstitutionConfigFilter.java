package com.example.parceltracker;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${VAR:default}} (and {@code ${VAR}}) placeholders in config values from
 * environment variables, falling back to the given default.
 *
 * <p>Helidon SE does not perform this shell/MicroProfile-style substitution natively — it
 * layers environment variables as a config source keyed by {@code A_B -> a.b}, which never
 * reaches hyphenated keys ({@code base-url}, {@code api-key}) or the explicitly-named env vars
 * documented in {@code application.yaml} ({@code AGGREGATOR_ENABLED}, {@code INPOST_BASE_URL},
 * …). Without this filter those {@code ${...}} defaults are taken literally, so e.g. the JDBC
 * URL would be the string {@code "${DB_URL:jdbc:...}"} and the service could not start.
 */
public final class EnvSubstitutionConfigFilter implements ConfigFilter {

    // ${NAME} or ${NAME:default}; default may be empty and may itself contain ':' (e.g. a JDBC URL).
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?}");

    @Override
    public String apply(Config.Key key, String stringValue) {
        if (stringValue == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(stringValue);
        if (!m.matches()) {
            return stringValue;
        }
        String envName = m.group(1);
        String fallback = m.group(2) == null ? "" : m.group(2);
        String fromEnv = System.getenv(envName);
        return fromEnv != null ? fromEnv : fallback;
    }
}
