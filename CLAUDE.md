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

## Architecture map — ports & adapters (hexagonal)

The application core depends only on **ports** (interfaces); concrete **adapters** implement
them and are wired together in the composition root (`Main.java`). Dependencies point inward:
adapters → application → domain. The domain never imports a framework.

```
domain/                         Pure records (Parcel, Address, GeoLocation, TrackingEvent, enums).
                                No Helidon/JDBC/Jackson imports.
application/
  port/in/  ParcelTrackingUseCase   Driving port — what the outside world can invoke.
  port/out/ ParcelStore             Driven port — persistence.
            Geocoder                Driven port — address → coordinates (fail-soft).
            CourierGateway          Driven port — courier detection + status fetch (fail-soft).
            CourierTrackingResult, DuplicateParcelException  — port value object / exception.
  service/  ParcelService           Core logic: implements the driving port, depends only on
                                    driven ports. Parallel geocode + fetch on virtual threads;
                                    refresh-on-read. No knowledge of HTTP/JDBC/couriers.
adapter/
  in/web/         ParcelEndpoint    Driving adapter — Helidon HttpService over the use-case port.
                  dto/Dtos          Wire DTOs, matching openapi.yaml exactly.
  out/persistence/ PostgresParcelRepository (implements ParcelStore), DataSourceFactory, schema.sql
  out/courier/    CourierRouter (implements CourierGateway), CourierClient SPI + the per-courier
                  clients: InPostCourierClient (free), AggregatorCourierClient (paid, off by default)
  out/geocoding/  NominatimGeocoder (implements Geocoder) — free Nominatim client
Main.java                       Composition root — the only place that names concrete adapters.
```

`src/main/resources/META-INF/openapi.yaml` is the source of truth for the HTTP contract —
served live at `/openapi.yaml`. If you change an endpoint's shape, update the spec file
*and* the OpenAPI doc *and* the relevant `specs/*/design.md` together.

## Conventions

- **Ports & adapters.** The core (`domain` + `application`) never imports an adapter or a
  framework. New I/O of any kind = a new driven port in `application/port/out` + an adapter
  in `adapter/out/*`, wired in `Main.java`. Depend on the port, never the concrete class.
- **Domain model is immutable records.** Updates go through `withX()` methods that return
  a new instance (see `Parcel.withRefreshedTracking`), never mutate in place.
- **Blocking code on virtual threads**, not reactive/callback chains — this is Helidon 4's
  model. Use `Executors.newVirtualThreadPerTaskExecutor()` for concurrent I/O (see
  `ParcelService.register`), not `CompletableFuture` chains.
- **Courier clients are pluggable.** Adding a courier = a new `CourierClient` implementation
  in `adapter/out/courier` + registering it in `Main.java`'s `CourierRouter` list. Don't
  special-case couriers inside `ParcelService` or the web layer.
- **Free-by-default.** Anything that costs money (the aggregator) must be opt-in via config
  and the service must run and be useful with it disabled.
- **DTOs are separate from domain records.** Don't leak `domain.*` types directly into JSON
  responses — keep the wire format (`adapter/in/web/dto`) decoupled from internal shape.

## Testing

No test suite exists yet. When adding one:
- Unit-test `CourierRouter` detection/routing logic and each `CourierClient`'s status
  mapping table against recorded fixture JSON (don't hit real courier APIs in tests).
- Integration-test `PostgresParcelRepository` against Testcontainers Postgres, not a mock.
- Integration-test the HTTP adapters (`InPostCourierClient`, `AggregatorCourierClient`,
  `NominatimGeocoder`) against a local WireMock stub seeded with recorded fixtures — this
  exercises the real request/response wiring without touching the network.
- Test `ParcelService` by injecting in-memory fakes at its ports (`ParcelStore`, `Geocoder`,
  `CourierGateway`) — no Docker, no HTTP. That fakeability is the whole point of the ports.

## Environment variables

See `application.yaml` for the full list and defaults. The ones you'll actually touch:

| Var | Purpose |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Postgres connection |
| `GEOCODING_USER_AGENT` | Required by Nominatim's usage policy — set to something real |
| `AGGREGATOR_ENABLED` / `AGGREGATOR_API_KEY` | Turn on non-InPost courier tracking |
