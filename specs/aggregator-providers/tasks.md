# Tasks: Pluggable aggregator providers

- [x] T1: `AggregatorProvider` SPI + `AggregatorProviders` registry (unknown id → fail fast) (R1)
- [x] T2: `TrackingMoreProvider` — extract the existing TrackingMore shape into a provider (R2)
- [x] T3: `SeventeenTrackProvider` — 17TRACK register + gettrackinfo, status map + events (R2)
- [x] T4: `Track123Provider` — Track123 query, status map + events (R2)
- [x] T5: `AggregatorCourierClient` delegates to the config-selected provider; provider-default
  base-url; fail-soft; disabled by default (R1, R2, R3)
- [x] T6: `application.yaml` — `provider` key, base-url default empty (R1, R3)
- [x] T7: Tests — registry, 17TRACK + Track123 WireMock providers, provider selection (R1, R2)
- [x] T8: Docs — CLAUDE.md/README env tables + aggregator description
