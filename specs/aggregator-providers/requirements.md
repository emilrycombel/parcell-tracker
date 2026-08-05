# Requirements: Pluggable aggregator providers

## Overview
The paid aggregator that covers non-InPost couriers should support more than one integration.
This adds a pluggable provider model so the operator can pick which tracking aggregator backs
the `CourierGateway` for non-InPost couriers — one active provider at a time, selected by config.

Providers shipped: **trackingmore** (existing default), **17track** (17TRACK), **track123**
(Track123).

## Requirements

### R1: One active provider, selected by config
- **WHEN** the aggregator is enabled **THE SYSTEM SHALL** route non-InPost couriers to the
  single provider named by `AGGREGATOR_PROVIDER` (`trackingmore` | `17track` | `track123`).
- **THE SYSTEM SHALL NOT** fan out to, or fail over across, multiple providers — exactly one is
  active, to keep cost predictable (each aggregator bills per lookup).
- **IF** `AGGREGATOR_PROVIDER` names an unknown provider **THEN THE SYSTEM SHALL** fail fast at
  startup with a message listing the known providers, rather than silently disabling.

### R2: Per-provider request + status mapping
- **WHEN** a provider is queried **THE SYSTEM SHALL** build the request and parse the response
  in that provider's own shape (endpoint, auth header, JSON paths), and map the provider's
  status vocabulary to the unified `ParcelStatus`.
- **IF** a provider call fails (HTTP error, parse error, unknown/absent status) **THEN THE
  SYSTEM SHALL** return `UNKNOWN` and never throw — same fail-soft contract as today.

### R3: Free-by-default preserved
- **WHERE** the aggregator is disabled (`AGGREGATOR_ENABLED=false` or no api key) **THE SYSTEM
  SHALL** leave non-InPost parcels `UNKNOWN`, regardless of which provider is selected.
- **WHERE** `AGGREGATOR_BASE_URL` is unset **THE SYSTEM SHALL** use the selected provider's
  default endpoint; when set it overrides.

## Out of scope
- Failover / load-balancing across providers, and provider auto-detection.
- Persisting 17TRACK/Track123 registration state (each fetch is self-contained).
- Provider-specific webhooks.

## Open questions / notes
- The exact JSON paths and status vocabularies for 17TRACK and Track123 are wired to their
  documented shapes but have **not** been validated against live responses (no API keys in this
  environment) — same caveat the original TrackingMore mapping carried. Confirm via `liveTest`
  with a real key, then adjust the provider's paths/status map only.
