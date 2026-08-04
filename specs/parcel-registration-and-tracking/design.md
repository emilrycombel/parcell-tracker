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

## Architecture: ports & adapters (hexagonal)

The code is organized as a hexagon: an application core that depends only on ports
(interfaces), with adapters implementing those ports at the edges. Dependencies point
inward (adapter → application → domain); the domain imports no framework.

- **Driving port** `ParcelTrackingUseCase` — the four operations (register / getRefreshed /
  list / delete) the outside world invokes. Kept as one cohesive port for this single
  feature rather than one interface per method: the operations share a lifecycle and are
  always wired together, so splitting them would add four constructor args at the composition
  root for no isolation benefit. Revisit if the feature grows enough that a driving adapter
  needs only a strict subset.
- **Driven ports** `ParcelStore`, `Geocoder`, `CourierGateway` — everything the core needs
  from the outside (persistence, geocoding, courier lookups). Fail-soft contracts (empty /
  `unknown()` rather than throwing) live on the ports, so the core's error handling doesn't
  depend on which adapter is behind them.
- **Adapters** implement the ports and are the only things that touch Helidon, JDBC, HTTP,
  or a specific courier/geocoder. Swapping Postgres for another store, or TrackingMore for
  another aggregator, is a one-adapter change — the core is untouched. This is also what
  makes the core unit-testable with in-memory fakes at the ports.

## Components touched

- `domain/*` — pure domain records
- `application/port/in/ParcelTrackingUseCase` — driving port
- `application/port/out/{ParcelStore,Geocoder,CourierGateway,CourierTrackingResult,DuplicateParcelException}` — driven ports
- `application/service/ParcelService` — core orchestration (implements the driving port)
- `adapter/out/persistence/{PostgresParcelRepository,DataSourceFactory}` — persistence adapter
- `adapter/out/geocoding/NominatimGeocoder` — Nominatim adapter
- `adapter/out/courier/{CourierRouter,CourierClient,InPostCourierClient,AggregatorCourierClient}` — courier adapter
- `adapter/in/web/{ParcelEndpoint,dto/Dtos}` — HTTP driving adapter
- `Main.java` — composition root (wires adapters to the core)

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
