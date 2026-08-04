package com.example.parceltracker;

import io.helidon.config.Config;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EnvSubstitutionConfigFilterTest {

    private final EnvSubstitutionConfigFilter filter = new EnvSubstitutionConfigFilter();

    @Test
    void resolvesDefault_whenVarUnset() {
        assertThat(filter.apply(null, "${DEFINITELY_UNSET_VAR_XYZ:fallback}")).isEqualTo("fallback");
    }

    @Test
    void resolvesFromEnv_whenVarSet() {
        String path = System.getenv("PATH"); // virtually always present
        assumeTrue(path != null && !path.isBlank(), "PATH not set in this environment");
        assertThat(filter.apply(null, "${PATH:ignored-default}")).isEqualTo(path);
    }

    @Test
    void emptyDefault_resolvesToEmptyString() {
        assertThat(filter.apply(null, "${UNSET_VAR_ABC:}")).isEmpty();
    }

    @Test
    void defaultMayContainColons() {
        assertThat(filter.apply(null, "${UNSET_DB_URL_VAR:jdbc:postgresql://localhost:5432/db}"))
                .isEqualTo("jdbc:postgresql://localhost:5432/db");
    }

    @Test
    void noDefault_unsetVar_resolvesToEmpty() {
        assertThat(filter.apply(null, "${UNSET_VAR_NO_DEFAULT}")).isEmpty();
    }

    @Test
    void nonPlaceholder_passesThrough() {
        assertThat(filter.apply(null, "plain-value")).isEqualTo("plain-value");
        assertThat(filter.apply(null, "8080")).isEqualTo("8080");
    }

    @Test
    void nullPassesThrough() {
        assertThat(filter.apply((Config.Key) null, (String) null)).isNull();
    }
}
