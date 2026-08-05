# Design: Pluggable aggregator providers

## Requirements coverage

| Requirement | Satisfied by |
|---|---|
| R1 (one active provider) | `AggregatorProviders` registry, `AGGREGATOR_PROVIDER` config, `AggregatorCourierClient` selection |
| R2 (per-provider shape) | `AggregatorProvider` impls: `TrackingMoreProvider`, `SeventeenTrackProvider`, `Track123Provider` |
| R3 (free-by-default) | `AggregatorCourierClient.enabled`, provider-default base-url |

## Approach

The aggregator stays a single catch-all `CourierClient` (priority 100) in the `CourierRouter`,
but its request/response handling is delegated to a pluggable **`AggregatorProvider`**:

```
interface AggregatorProvider {
    String id();               // "trackingmore" | "17track" | "track123"
    String defaultBaseUrl();   // used when AGGREGATOR_BASE_URL is unset
    CourierTrackingResult fetch(HttpClient http, String baseUrl, String apiKey,
                                String trackingNumber, ObjectMapper mapper) throws Exception;
}
```

`fetch` owns the whole HTTP dance for that provider (some need more than one call — see 17TRACK),
so the abstraction isn't tied to a single request/response pair. Providers are **stateless
singletons** held in a small registry:

```
AggregatorProviders.byId("17track") -> SeventeenTrackProvider   // unknown id -> IllegalArgumentException
```

`AggregatorCourierClient` reads `provider` + `base-url` + `api-key` + `enabled` from config,
resolves the one provider via the registry (fail-fast on an unknown id, R1), and in
`fetchTracking` delegates to it inside a try/catch that maps any failure to `unknown()` (R2's
fail-soft). `supports()` is unchanged: catch-all when enabled. Only one aggregator instance is
ever in the router, so there is no multi-provider fan-out (R1).

Provider selection lives inside the adapter (the registry), so `Main` is unchanged — it still
wires `new AggregatorCourierClient(config.get("courier.aggregator"), mapper)`.

## Providers

| id | default base-url | auth | request | status source |
|---|---|---|---|---|
| `trackingmore` | `https://api.trackingmore.com/v4/trackings` | `Tracking-Api-Key` header | `GET /get?tracking_numbers=` | `data[0].delivery_status` + `origin_info.trackinfo[]` |
| `17track` | `https://api.17track.net/track/v2.2` | `17token` header | `POST /register` then `POST /gettrackinfo`, body `[{"number":n}]` | `data.accepted[0].track_info.latest_status.status` + `…tracking.providers[].events[]` |
| `track123` | `https://api.track123.com/gateway/open-api/tk/v2/track` | `Track123-Api-Secret` header | `POST /query`, body `{"trackNos":[n]}` | `data.content[0].statusInfo` + `…providersList[].trackingDetailList[]` |

17TRACK is register-then-poll: `fetch` does a best-effort `register` (idempotent for an
already-tracked number) then `gettrackinfo`. Data can lag on the first fetch (17TRACK queries
the carrier asynchronously) — acceptable under fail-soft: status/events just fill in on a later
refresh. Each provider has its own status-vocabulary → `ParcelStatus` map.

> **Unverified paths.** The 17TRACK and Track123 JSON paths + status maps are coded to their
> documented shapes but not validated against live responses (no keys here). Confirm with a real
> key via `./gradlew liveTest`, then adjust the affected provider only — the router/service/other
> providers don't change.

## Config

`application.yaml` `courier.aggregator`: adds `provider` (`${AGGREGATOR_PROVIDER:trackingmore}`)
and changes `base-url` to default empty (`${AGGREGATOR_BASE_URL:}` → provider default). `enabled`
and `api-key` unchanged. Disabled out of the box.

## Components touched

- `adapter/out/courier/AggregatorProvider` (port-like SPI), `AggregatorProviders` (registry)
- `adapter/out/courier/{TrackingMoreProvider,SeventeenTrackProvider,Track123Provider}`
- `adapter/out/courier/AggregatorCourierClient` — now delegates to the selected provider
- `application.yaml` — `provider` key + base-url default

## Testing

- `AggregatorProvidersTest` — registry: known ids resolve, unknown throws, case-insensitive.
- `SeventeenTrackProviderTest`, `Track123ProviderTest` — WireMock stubs seeded with each
  provider's documented shape: status mapping, event extraction, empty/`rejected` → unknown.
- `AggregatorCourierClientTest` — existing TrackingMore coverage plus a provider-selection case
  (`provider=17track` posts to `/gettrackinfo`).
- The mappings are fixture-verified; the live path stays behind `@Tag("live")`.
