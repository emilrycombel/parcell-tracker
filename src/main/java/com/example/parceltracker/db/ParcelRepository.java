package com.example.parceltracker.db;

import com.example.parceltracker.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.postgresql.util.PGobject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class ParcelRepository {

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final CollectionType eventListType;

    public ParcelRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.eventListType = mapper.getTypeFactory().constructCollectionType(List.class, TrackingEvent.class);
    }

    public Parcel insert(Parcel p) {
        String sql = """
            INSERT INTO parcels
                (id, tracking_number, courier, status,
                 street, house_number, apartment_number, city, postal_code, country,
                 latitude, longitude, geocode_provider, geocoded_at,
                 events, created_at, last_refreshed_at)
            VALUES (?,?,?,?, ?,?,?,?,?,?, ?,?,?,?, ?::jsonb, ?, ?)
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindAll(ps, p);
            ps.executeUpdate();
            return p;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) { // unique_violation
                throw new DuplicateParcelException(p.trackingNumber(), p.courier(), e);
            }
            throw new RuntimeException("Failed to insert parcel", e);
        }
    }

    public Optional<Parcel> findById(UUID id) {
        String sql = "SELECT * FROM parcels WHERE id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parcel " + id, e);
        }
    }

    public List<Parcel> findAll(int page, int size, ParcelStatus statusFilter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM parcels");
        if (statusFilter != null) {
            sql.append(" WHERE status = ?");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int idx = 1;
            if (statusFilter != null) {
                ps.setString(idx++, statusFilter.name());
            }
            ps.setInt(idx++, size);
            ps.setInt(idx, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                List<Parcel> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list parcels", e);
        }
    }

    /** Persists a status/events/geolocation refresh produced by the service layer. */
    public void update(Parcel p) {
        String sql = """
            UPDATE parcels SET
                status = ?, latitude = ?, longitude = ?, geocode_provider = ?, geocoded_at = ?,
                events = ?::jsonb, last_refreshed_at = ?
            WHERE id = ?
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.status().name());
            if (p.geoLocation() != null) {
                ps.setDouble(2, p.geoLocation().latitude());
                ps.setDouble(3, p.geoLocation().longitude());
                ps.setString(4, p.geoLocation().provider());
                ps.setTimestamp(5, Timestamp.from(p.geoLocation().geocodedAt()));
            } else {
                ps.setNull(2, Types.DOUBLE);
                ps.setNull(3, Types.DOUBLE);
                ps.setNull(4, Types.VARCHAR);
                ps.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            ps.setString(6, writeEvents(p.events()));
            ps.setTimestamp(7, p.lastRefreshedAt() != null ? Timestamp.from(p.lastRefreshedAt()) : null);
            ps.setObject(8, p.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update parcel " + p.id(), e);
        }
    }

    public boolean delete(UUID id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM parcels WHERE id = ?")) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete parcel " + id, e);
        }
    }

    // --- mapping helpers -------------------------------------------------

    private void bindAll(PreparedStatement ps, Parcel p) throws SQLException {
        ps.setObject(1, p.id());
        ps.setString(2, p.trackingNumber());
        ps.setString(3, p.courier().name());
        ps.setString(4, p.status().name());

        Address a = p.deliveryAddress();
        ps.setString(5, a.street());
        ps.setString(6, a.houseNumber());
        ps.setString(7, a.apartmentNumber());
        ps.setString(8, a.city());
        ps.setString(9, a.postalCode());
        ps.setString(10, a.country());

        GeoLocation g = p.geoLocation();
        if (g != null) {
            ps.setDouble(11, g.latitude());
            ps.setDouble(12, g.longitude());
            ps.setString(13, g.provider());
            ps.setTimestamp(14, Timestamp.from(g.geocodedAt()));
        } else {
            ps.setNull(11, Types.DOUBLE);
            ps.setNull(12, Types.DOUBLE);
            ps.setNull(13, Types.VARCHAR);
            ps.setNull(14, Types.TIMESTAMP_WITH_TIMEZONE);
        }

        ps.setString(15, writeEvents(p.events()));
        ps.setTimestamp(16, Timestamp.from(p.createdAt()));
        ps.setTimestamp(17, p.lastRefreshedAt() != null ? Timestamp.from(p.lastRefreshedAt()) : null);
    }

    private Parcel mapRow(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        Address address = new Address(
                rs.getString("street"),
                rs.getString("house_number"),
                rs.getString("apartment_number"),
                rs.getString("city"),
                rs.getString("postal_code"),
                rs.getString("country")
        );

        GeoLocation geo = null;
        Object lat = rs.getObject("latitude");
        if (lat != null) {
            geo = new GeoLocation(
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("geocode_provider"),
                    toInstant(rs.getTimestamp("geocoded_at"))
            );
        }

        Timestamp lastRefreshed = rs.getTimestamp("last_refreshed_at");

        return new Parcel(
                id,
                rs.getString("tracking_number"),
                Courier.valueOf(rs.getString("courier")),
                ParcelStatus.valueOf(rs.getString("status")),
                address,
                geo,
                readEvents(rs.getString("events")),
                toInstant(rs.getTimestamp("created_at")),
                lastRefreshed != null ? lastRefreshed.toInstant() : null
        );
    }

    private Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private String writeEvents(List<TrackingEvent> events) {
        try {
            return mapper.writeValueAsString(events == null ? List.of() : events);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize events", e);
        }
    }

    private List<TrackingEvent> readEvents(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return mapper.readValue(json, eventListType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize events", e);
        }
    }

    public static final class DuplicateParcelException extends RuntimeException {
        public DuplicateParcelException(String trackingNumber, Courier courier, Throwable cause) {
            super("Parcel already registered: " + trackingNumber + " / " + courier, cause);
        }
    }
}
