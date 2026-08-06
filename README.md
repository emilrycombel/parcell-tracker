# Parcel Tracker

Unified parcel tracking service for Polish couriers, spec-first (see
`src/main/resources/META-INF/openapi.yaml` — served live at `/openapi.yaml`).

## Design

- **Helidon SE 4**, blocking-style handlers on virtual threads (no reactive/callback code).
- **PostgreSQL** for storage — indexed on status and tracking number, JSONB for the event
  history, lat/lon columns ready for future "parcels near X" queries.
- **InPost**: free, direct, no API key — `GET /v1/tracking/{number}` on InPost's public
  ShipX tracking endpoint. Tracking-number regex (`^\d{20,26}$`) auto-detects InPost when
  the caller doesn't specify a courier.
- **Everything else** (DPD, DHL, GLS, Poczta Polska, UPS, FedEx): routed to a pluggable
  `AggregatorCourierClient` backed by one of several providers — **TrackingMore** (default),
  **17TRACK**, or **Track123** — selected via `AGGREGATOR_PROVIDER`. It's **disabled until you
  set `AGGREGATOR_ENABLED=true` and `AGGREGATOR_API_KEY=...`**, so the service still runs
  entirely free out of the box for InPost-only use, and non-InPost parcels simply stay
  `UNKNOWN` until you plug one in. Adding another aggregator = one `AggregatorProvider`
  implementation in `adapter/out/courier` registered in `AggregatorProviders`.
- **Geocoding**: **opt-in and disabled by default** — set `GEOCODING_BASE_URL` to a
  Nominatim-compatible `/search` endpoint to enable it, otherwise parcels register without
  coordinates. Production should use a self-hosted/controlled Nominatim (OSM policy forbids
  package/vehicle-tracking services on the public instance); for local dev only you can point
  it at `https://nominatim.openstreetmap.org/search`. When enabled, the result is cached on the
  parcel row (geocoded once per address) and the ~1 req/sec policy is respected.
- Registration runs geocoding and the initial courier fetch **concurrently** on virtual
  threads. `GET /parcels/{id}` refreshes from the courier before returning, but not more
  often than `REFRESH_MIN_INTERVAL_SECONDS` per parcel (default 300; 0 = always refresh).

## Run locally

```bash
docker compose up -d          # Postgres on localhost:5432
./gradlew run                 # compile + start the server

# or build a runnable distribution:
./gradlew installDist
./build/install/parcel-tracker/bin/parcel-tracker
```

Env vars (all optional, see `application.yaml` for defaults):

| Var | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Postgres connection |
| `GEOCODING_BASE_URL` | Nominatim-compatible `/search` endpoint. Unset = geocoding disabled. Prod: self-hosted Nominatim; dev only: `https://nominatim.openstreetmap.org/search` |
| `GEOCODING_USER_AGENT` | Set this to something identifying your app — Nominatim requires it |
| `GEOCODING_MIN_INTERVAL_MS` | Min spacing between geocoding calls (default 1000 = ~1 req/s) |
| `REFRESH_MIN_INTERVAL_SECONDS` | Throttle: min seconds between courier refreshes per parcel on GET (default 300; 0 disables) |
| `AGGREGATOR_ENABLED`, `AGGREGATOR_API_KEY` | Enable non-InPost courier tracking |
| `AGGREGATOR_PROVIDER` | `trackingmore` (default), `17track`, or `track123` |
| `AGGREGATOR_BASE_URL` | Override the selected provider's default endpoint (optional) |

## Example requests

Register (InPost auto-detected from the tracking number format):

```bash
curl -X POST localhost:8080/api/v1/parcels \
  -H "Content-Type: application/json" \
  -d '{
    "trackingNumber": "590123456789012345678901",
    "externalId": "ORDER-2026-000123",
    "deliveryAddress": {
      "street": "Marszałkowska",
      "houseNumber": "1",
      "city": "Warszawa",
      "postalCode": "00-001",
      "country": "PL"
    }
  }'
```

`externalId` is optional — your own order reference. List all parcels linked to an order
(useful for displaying an order's shipments together; several parcels may share one id):

```bash
curl "localhost:8080/api/v1/parcels?externalId=ORDER-2026-000123"
```

Fetch (refreshes the status from InPost first, unless it was refreshed within
`REFRESH_MIN_INTERVAL_SECONDS`):

```bash
curl localhost:8080/api/v1/parcels/{id}
```

## Notes / next steps if you outgrow this

- Schema is applied via a raw `schema.sql` on boot — fine here, move to Flyway/Liquibase
  once you have real migrations to manage.
- Aggregator integrations are pluggable `AggregatorProvider`s (TrackingMore, 17TRACK,
  Track123). Adding another (e.g. Ship24, AfterShip) = one provider class registered in
  `AggregatorProviders` — the router/service layer doesn't care which is behind it. The
  17TRACK/Track123 JSON paths are wired to their documented shapes; validate against a live
  key via `./gradlew liveTest` before relying on them (see `specs/aggregator-providers/`).
- No auth/rate-limiting is included; add an API-key filter before exposing this publicly.
- The project compiles cleanly under Gradle (Java 21, Helidon 4.1.4); the Helidon 4
  media/Jackson wiring in `Main.java` builds without adjustment. Runtime against a live
  Postgres + real courier endpoints is still worth a manual smoke test.
