# Tasks: Parcel registration and tracking

- [x] T1: Domain model records (`Parcel`, `Address`, `GeoLocation`, `TrackingEvent`, enums) (R1, R2)
- [x] T2: Postgres schema + `ParcelRepository` (insert/find/update/delete, JSONB events) (R1, R2, R4)
- [x] T3: `GeocodingService` (Nominatim, free, fail-soft) (R1, R2)
- [x] T4: `CourierClient` interface + `InPostCourierClient` (free, direct, status mapping) (R1, R2, R3)
- [x] T5: `AggregatorCourierClient` (paid fallback, disabled by default) (R3)
- [x] T6: `CourierRouter` (priority-ordered dispatch, InPost auto-detection) (R3)
- [x] T7: `ParcelService` — register (parallel geocode + fetch), refresh-on-read (R1, R2)
- [x] T8: `ParcelEndpoint` + DTOs matching `openapi.yaml` (R1, R2, R4)
- [x] T9: `openapi.yaml` as source of truth, served at `/openapi.yaml`
- [ ] T10: Test suite (unit + Testcontainers) — not yet written, see `CLAUDE.md`
- [ ] T11: Verify `mvn compile` in an environment with network access — unverified so far
