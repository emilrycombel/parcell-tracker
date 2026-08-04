package com.example.parceltracker.adapter.out.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static HikariDataSource create(Config config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.get("url").asString().get());
        hikari.setUsername(config.get("user").asString().get());
        hikari.setPassword(config.get("password").asString().get());
        hikari.setMaximumPoolSize(config.get("pool-size").asInt().orElse(10));
        hikari.setPoolName("parcel-tracker-pool");
        return new HikariDataSource(hikari);
    }

    /** Applies schema.sql on startup. Fine for this service's size; use Flyway/Liquibase if it grows. */
    public static void applySchema(HikariDataSource ds) {
        try (InputStream in = DataSourceFactory.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            String sql;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                reader.lines().forEach(line -> sb.append(line).append('\n'));
                sql = sb.toString();
            }
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        } catch (IOException | java.sql.SQLException e) {
            throw new IllegalStateException("Failed applying schema", e);
        }
    }
}
