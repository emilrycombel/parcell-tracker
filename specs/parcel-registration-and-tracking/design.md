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
- `adapter/out/geocoding/{NominatimGeocoder,DisabledGeocoder}` — geocoding adapters (opt-in;
  `Main.geocoder` picks between them based on `GEOCODING_BASE_URL`)
- `adapter/out/courier/{CourierRouter,CourierClient,InPostCourierClient,AggregatorCourierClient}` — courier adapter
- `adapter/in/web/{ParcelEndpoint,dto/Dtos}` — HTTP driving adapter
- `Main.java` — composition root (wires adapters to the core)

## Error handling

- Duplicate registration → `application.port.out.DuplicateParcelException` (thrown by the
  Postgres adapter on a unique-constraint violation, SQLSTATE 23505) → mapped to 409 in
  `ParcelEndpoint`.
- Geocoding failure → caught inside `NominatimGeocoder.geocode` (the `Geocoder` port),
  returns `Optional.empty()`, never propagates — registration proceeds without coordinates.
- Courier call failure/timeout → caught inside each `CourierClient.fetchTracking`, returns
  `CourierTrackingResult.unknown()` rather than throwing — refresh keeps the prior status.
- Malformed UUID in path → 400, not a 500 from a parse exception bubbling up.
- Malformed request body / invalid list query params (page, size, status) → 400 in
  `ParcelEndpoint`, not 500. Internal exception text is logged server-side, never returned.
- Malformed event timestamp → `TrackingTimestamps.parseOrElse` falls back per-event to
  "now" rather than throwing, so one bad date doesn't discard the whole tracking result.

## Rate limiting & refresh throttle

- **Refresh-on-read throttle.** `ParcelService` takes a `minRefreshInterval` (from
  `REFRESH_MIN_INTERVAL_SECONDS`, default 300) and a `Clock`. `getRefreshed` serves stored
  state without calling the courier if the parcel was refreshed within that window — bounding
  courier API usage/cost when a single parcel is polled frequently. `Duration.ZERO` disables
  it (used by the no-arg-interval convenience constructor and most unit tests).
- **Nominatim rate limit.** `NominatimGeocoder` serializes outbound requests to at least
  `GEOCODING_MIN_INTERVAL_MS` apart (default 1000) to respect Nominatim's ~1 req/sec policy.
  Cheap because geocoding runs on virtual threads and happens once per address.

## Geocoding endpoint (opt-in) — the URL to configure

Geocoding is **disabled by default**. The composition root (`Main.geocoder`) wires a real
`NominatimGeocoder` only when `GEOCODING_BASE_URL` is set; otherwise it wires `DisabledGeocoder`
(always returns empty), so parcels register without coordinates. This mirrors the aggregator's
"opt-in, off by default" model and keeps the service off the public Nominatim instance unless an
operator deliberately points at one.

To enable geocoding, set `GEOCODING_BASE_URL` to a Nominatim-compatible `/search` endpoint:

| Environment | `GEOCODING_BASE_URL` |
|---|---|
| Production | A **self-hosted / controlled** Nominatim, e.g. `https://nominatim.internal.example.com/search`. OSM's usage policy forbids package/vehicle-tracking services from using the public instance, and it may block such requests. |
| Local dev only | `https://nominatim.openstreetmap.org/search` (respect the ~1 req/sec policy; set a real `GEOCODING_USER_AGENT`). |

When enabled, the address → lat/lon result is cached on the parcel row (geocoded once per
address) and refresh backfills it if it was missing. All of this is fail-soft: an unreachable or
unconfigured endpoint never blocks registration.
- **Event merge.** `ParcelService.mergeEvents` keys events by (timestamp, raw status) into a
  union and sorts by time — replacing the earlier size comparison, which could drop events on
  reorder/shorter feeds.

## Concurrent refresh: optimistic locking (T14)

`getRefreshed` reads → fetches the courier (slow I/O, no lock) → persists. To stop two parallel
refreshes of the same parcel from clobbering each other, the write is version-checked rather
than unconditional:

- `parcels` has a `version BIGINT` column, carried on the `Parcel` record.
- `ParcelStore.update` applies `… SET …, version = version + 1 WHERE id = ? AND version = ?`
  and returns `true` only if a row matched (no concurrent commit in between); `false` on a lost
  race. The Postgres adapter and the in-memory fake both honor this.
- `ParcelService` wraps the persist in a bounded retry (`MAX_REFRESH_ATTEMPTS`): on a `false`,
  it reloads the now-current parcel and re-runs `applyTracking` — re-merging its already-fetched
  events onto the latest state (the merge is a union, so nothing is lost). If the reloaded row is
  now within the refresh window (a concurrent refresh just updated it), it returns that instead
  of writing again. The courier is fetched once, not per attempt.

Holding a row lock across the courier HTTP call was rejected (slow I/O under lock); optimistic
locking + retry keeps the lock-free read/fetch and only contends on the short write.

## Testing strategy

Implemented and layered along the ports (see `CLAUDE.md` Testing for detail):
- `ParcelServiceTest` + `ParcelServiceRefreshThrottleTest` drive the core through in-memory
  port fakes (`InMemoryParcelStore`, `FakeGeocoder`, `FakeCourierGateway`).
- `CourierRouterTest` covers detection/priority dispatch as a unit.
- `InPostCourierClientTest`, `AggregatorCourierClientTest`, `NominatimGeocoderTest` run the
  real HTTP clients against a WireMock stub seeded with fixtures.
- `PostgresParcelRepositoryIT` runs against Testcontainers Postgres (skipped without Docker).
- Opt-in `@Tag("live")` smoke tests hit the real endpoints via `./gradlew liveTest`.
