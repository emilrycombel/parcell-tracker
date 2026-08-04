CREATE TABLE IF NOT EXISTS parcels (
    id                 UUID PRIMARY KEY,
    tracking_number    VARCHAR(64)  NOT NULL,
    external_id        VARCHAR(128),
    courier            VARCHAR(32)  NOT NULL,
    status             VARCHAR(32)  NOT NULL,

    street             VARCHAR(128) NOT NULL,
    house_number       VARCHAR(16)  NOT NULL,
    apartment_number    VARCHAR(16),
    city               VARCHAR(128) NOT NULL,
    postal_code        VARCHAR(16)  NOT NULL,
    country            VARCHAR(2)   NOT NULL DEFAULT 'PL',

    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    geocode_provider   VARCHAR(32),
    geocoded_at        TIMESTAMPTZ,

    events             JSONB        NOT NULL DEFAULT '[]',

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_refreshed_at  TIMESTAMPTZ,

    CONSTRAINT uq_parcels_tracking UNIQUE (tracking_number, courier)
);

CREATE INDEX IF NOT EXISTS idx_parcels_status ON parcels (status);
CREATE INDEX IF NOT EXISTS idx_parcels_tracking_number ON parcels (tracking_number);
-- Look up all parcels linked to a caller's order reference
CREATE INDEX IF NOT EXISTS idx_parcels_external_id ON parcels (external_id);
-- Speeds up any future "parcels near this point" queries
CREATE INDEX IF NOT EXISTS idx_parcels_geo ON parcels (latitude, longitude);
