# CLAUDE.md

Guidance for Claude Code (and any human reading over its shoulder) working in this repo.

## What this is

A Helidon SE 4 service that unifies parcel tracking across Polish couriers. InPost is
tracked for free via its public ShipX endpoint; every other courier (DPD, DHL, GLS,
Poczta Polska, UPS, FedEx) routes through a pluggable paid aggregator client. Delivery
addresses are geocoded for free via OpenStreetMap Nominatim. Postgres is the store.

## Spec-driven workflow — read this first

This repo is spec-driven. Before writing or changing code:

1. Find or create the relevant feature folder under `specs/<feature-name>/`.
2. Read `requirements.md` (what the feature must do, in EARS format) and `design.md`
   (how it's built) before touching code. If they don't exist yet for the feature you're
   about to build, write them first — use `specs/_template/` as the starting point.
3. Work through `tasks.md` top to bottom, checking items off as you complete them. Each
   task references the requirement ID(s) it satisfies.
4. If an implementation decision diverges from what `design.md` says, update `design.md`
   in the same change — don't let the docs drift from the code.
5. New feature = new folder under `specs/`. Don't pile unrelated features into one spec.

Current specs:
- `specs/parcel-registration-and-tracking/` — the initial feature set (register, refresh,
  list, delete parcels). Status: implemented, see its `tasks.md`.

## Build & run

```bash
docker compose up -d        # Postgres on localhost:5432
./gradlew run               # compile + start the server (dev)
```

To build a runnable distribution instead:

```bash
./gradlew installDist                        # assembles build/install/parcel-tracker/
./build/install/parcel-tracker/bin/parcel-tracker
```

```bash
./gradlew test              # run tests (see Testing below)
```

This is a **Gradle** build (Kotlin DSL — `build.gradle.kts`), Java 21 toolchain. The
`application` plugin provides `run` and `installDist`. `./gradlew compileJava` is a quick
compile check; the whole project compiles cleanly against Helidon 4.1.4, including the
media/Jackson wiring in `Main.java`.

## Architecture map

```
model/       Immutable domain records (Parcel, Address, GeoLocation, TrackingEvent, enums)
db/          ParcelRepository (JDBC + HikariCP), DataSourceFactory, schema.sql
geocoding/   GeocodingService — free Nominatim client
courier/     CourierClient interface, InPostCourierClient (free), AggregatorCourierClient
             (paid, disabled by default), CourierRouter (picks between them)
service/     ParcelService — registration (parallel geocode + initial fetch via virtual
             threads) and refresh-on-read
web/         ParcelEndpoint (HttpService routing) + DTOs matching openapi.yaml exactly
```

`src/main/resources/META-INF/openapi.yaml` is the source of truth for the HTTP contract —
served live at `/openapi.yaml`. If you change an endpoint's shape, update the spec file
*and* the OpenAPI doc *and* the relevant `specs/*/design.md` together.

## Conventions

- **Domain model is immutable records.** Updates go through `withX()` methods that return
  a new instance (see `Parcel.withRefreshedTracking`), never mutate in place.
- **Blocking code on virtual threads**, not reactive/callback chains — this is Helidon 4's
  model. Use `Executors.newVirtualThreadPerTaskExecutor()` for concurrent I/O (see
  `ParcelService.register`), not `CompletableFuture` chains.
- **Courier clients are pluggable.** Adding a courier = a new `CourierClient` implementation
  + registering it in `Main.java`'s `CourierRouter` list. Don't special-case couriers
  inside `ParcelService` or the web layer.
- **Free-by-default.** Anything that costs money (the aggregator) must be opt-in via config
  and the service must run and be useful with it disabled.
- **DTOs (`web/dto/Dtos.java`) are separate from domain records.** Don't leak `model.*`
  types directly into JSON responses — keep the wire format decoupled from internal shape.

## Testing

No test suite exists yet. When adding one:
- Unit-test `CourierRouter` detection/routing logic and each `CourierClient`'s status
  mapping table against recorded fixture JSON (don't hit real courier APIs in tests).
- Integration-test `ParcelRepository` against Testcontainers Postgres, not a mock.
- `GeocodingService` and courier clients should be fakeable via the same interfaces they
  already implement — inject fakes in `ParcelService` tests rather than hitting the network.

## Environment variables

See `application.yaml` for the full list and defaults. The ones you'll actually touch:

| Var | Purpose |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Postgres connection |
| `GEOCODING_USER_AGENT` | Required by Nominatim's usage policy — set to something real |
| `AGGREGATOR_ENABLED` / `AGGREGATOR_API_KEY` | Turn on non-InPost courier tracking |
