# Design: Parcel registration and tracking

## Requirements coverage

| Requirement | Satisfied by |
|---|---|
| R1 (register) | `ParcelService.register`, `ParcelEndpoint.register`, `ParcelRepository.insert` |
| R2 (refresh on read) | `ParcelService.getRefreshed`, `ParcelEndpoint.getOne` |
| R3 (courier routing) | `CourierRouter`, `InPostCourierClient`, `AggregatorCourierClient` |
| R4 (list/delete) | `ParcelService.list`/`delete`, `ParcelEndpoint.list`/`delete` |

## Approach

Two courier clients behind one `CourierClient` interface, ordered by `priority()` in
`CourierRouter` (InPost = 0, aggregator = 100). The router is a plain sorted-list scan,
not a plugin registry — fine at this scale (2 clients); revisit if the courier count
grows enough that ordering-by-priority stops being an adequate dispatch strategy.

Considered making the aggregator required (fail registration if unconfigured) — rejected
because it breaks the "free out of the box" goal (see prior conversation on tracking API
pricing at ~1k parcels/month). `UNKNOWN` status for unconfigured couriers was chosen over
an error so the endpoint contract doesn't change based on config.

Geocoding and initial courier fetch run on `Executors.newVirtualThreadPerTaskExecutor()`
via two `Future`s rather than `StructuredTaskScope` — kept off preview APIs so the build
doesn't depend on which exact Java 21-26 patch enables them as stable.

## Data model

Single `parcels` table (see `schema.sql`), one row per (tracking_number, courier) pair —
enforced with a unique constraint, which is what backs the 409-on-duplicate requirement.
Events are stored as JSONB rather than a child table: they're always read/written as a
whole list per parcel, never queried individually, so a child table would add join cost
for no query benefit. Revisit if per-event querying becomes a real need.

## API contract

See `META-INF/openapi.yaml`, paths `/parcels` and `/parcels/{id}`. No changes pending.

## Components touched

- `model/*` — domain records
- `db/ParcelRepository`, `db/DataSourceFactory` — persistence
- `geocoding/GeocodingService` — Nominatim client
- `courier/*` — client interface + InPost/aggregator implementations + router
- `service/ParcelService` — orchestration
- `web/ParcelEndpoint`, `web/dto/Dtos` — HTTP layer
- `Main.java` — wiring

## Error handling

- Duplicate registration → `ParcelRepository.DuplicateParcelException` on unique-constraint
  violation (SQLSTATE 23505) → mapped to 409 in `ParcelEndpoint`.
- Geocoding failure → caught inside `GeocodingService.geocode`, returns `Optional.empty()`,
  never propagates — registration proceeds without coordinates.
- Courier call failure/timeout → caught inside each `CourierClient.fetchTracking`, returns
  `CourierTrackingResult.unknown()` rather than throwing — refresh keeps the prior status.
- Malformed UUID in path → 400, not a 500 from a parse exception bubbling up.

## Testing strategy

Not yet implemented (see `CLAUDE.md` Testing section for the intended shape: fixture-based
unit tests for status-mapping tables, Testcontainers for the repository, fakes for
`GeocodingService`/`CourierClient` in service-layer tests).
