# Tasks: Parcel registration and tracking

- [x] T1: Domain model records (`Parcel`, `Address`, `GeoLocation`, `TrackingEvent`, enums) (R1, R2)
- [x] T2: Postgres schema + `ParcelRepository` (insert/find/update/delete, JSONB events) (R1, R2, R4)
- [x] T3: `GeocodingService` (Nominatim, free, fail-soft) (R1, R2)
- [x] T4: `CourierClient` interface + `InPostCourierClient` (free, direct, status mapping) (R1, R2, R3)
- [x] T5: `AggregatorCourierClient` (paid fallback, disabled by default) (R3)
- [x] T6: `CourierRouter` (priority-ordered dispatch, InPost auto-detection) (R3)
- [x] T7: `ParcelService` — register (parallel geocode + fetch), refresh-on-read (R1, R2)
- [x] T8: `ParcelEndpoint` + DTOs matching `openapi.yaml` (R1, R2, R4)
- [x] T9: `openapi.yaml` as source of truth, served at `/openapi.yaml` (R1, R2, R4)
- [x] T10: Test suite — unit (`CourierRouter`), application-core via port fakes
  (`ParcelService`), WireMock HTTP-adapter tests (InPost/aggregator/Nominatim), and a
  Testcontainers Postgres IT (skipped without Docker). 37 tests. (R1, R2, R3, R4)
- [x] T11: Verify the build compiles — done via Gradle (`./gradlew build`, Java 21, Helidon 4.1.4) (R1, R2, R3, R4)
- [x] T12: Robustness refinements — refresh-on-read throttle, Nominatim ~1 req/s rate limit,
  union/dedupe event merge, per-event date tolerance (R2, R3)
- [x] T13: Gated live-sandbox smoke tests (`@Tag("live")`, `./gradlew liveTest`) for InPost,
  Nominatim, and the aggregator — opt-in via env, excluded from the default suite/CI (R1, R2, R3)
- [x] T14: Optimistic locking for concurrent refresh — `version` column + version-checked
  `ParcelStore.update` (boolean) + bounded reload-and-remerge retry in `ParcelService`. Postgres
  IT (`update_isOptimisticallyLocked_onVersion`) + service test (`ParcelServiceConcurrencyTest`).
  See design.md "Concurrent refresh: optimistic locking". (R2)
- [x] T15: Startup smoke test (`ApplicationSmokeIT`) — boots the real wiring via `Main.start`
  against Testcontainers Postgres and drives it over HTTP (config/`${VAR:default}`, datasource,
  schema, web/media/routing). Skipped without Docker. (R1, R2, R4)
- [x] T16: Geocoding is opt-in — disabled unless `GEOCODING_BASE_URL` is set (`DisabledGeocoder`
  vs `NominatimGeocoder`, chosen in `Main.geocoder`); off the public Nominatim instance by
  default. Endpoint to configure documented in design.md. (R1)
