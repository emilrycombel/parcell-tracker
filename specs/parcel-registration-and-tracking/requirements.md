# Requirements: Parcel registration and tracking

## Overview
Register a parcel by tracking number and delivery address; the service geocodes the
address and fetches the current courier status. Every subsequent read refreshes the
status from the courier before returning it. Covers InPost directly (free) and other
Polish couriers via an optional paid aggregator.

## Requirements

### R1: Register a parcel
- **WHEN** a client POSTs a tracking number and delivery address **THE SYSTEM SHALL**
  create a parcel record and return it with a generated ID.
- **WHEN** no courier is specified **THE SYSTEM SHALL** auto-detect InPost from the
  tracking number format (20-26 digit numeric string), otherwise mark it `UNKNOWN`.
- **WHEN** a parcel is registered **THE SYSTEM SHALL** geocode the delivery address and
  fetch the initial courier status concurrently, not sequentially.
- **IF** a parcel with the same tracking number and courier is already registered **THEN
  THE SYSTEM SHALL** reject the request with 409 Conflict rather than creating a duplicate.
- **IF** geocoding fails **THEN THE SYSTEM SHALL** still register the parcel with no
  geolocation, rather than failing the whole registration.

### R2: Refresh status on read
- **WHEN** a client GETs a parcel by ID **THE SYSTEM SHALL** call the courier for the
  latest status before returning the parcel, and persist the refreshed state.
- **IF** the courier call fails or returns nothing new **THEN THE SYSTEM SHALL** return
  the last known good status rather than overwriting it with `UNKNOWN`.
- **IF** a parcel has no geolocation yet (e.g. geocoding failed at registration) **THEN
  THE SYSTEM SHALL** retry geocoding on refresh.

### R3: Courier routing
- **WHEN** the courier is InPost **THE SYSTEM SHALL** use the free direct ShipX tracking
  endpoint, never the paid aggregator.
- **WHEN** the courier is anything else **THE SYSTEM SHALL** route to the configured
  aggregator client.
- **IF** the aggregator is not configured (no API key / disabled) **THEN THE SYSTEM
  SHALL** leave non-InPost parcels at status `UNKNOWN` rather than erroring, so the
  service remains usable with zero paid dependencies.

### R4: List and delete
- **WHEN** a client GETs the parcel collection **THE SYSTEM SHALL** return a paginated
  list, optionally filtered by status, without triggering a courier refresh for every
  item (refresh is per-parcel, on individual GET, to avoid rate-limit/cost blowup on list).
- **WHEN** a client DELETEs a parcel by ID **THE SYSTEM SHALL** remove it and return 204,
  or 404 if it doesn't exist.

## Out of scope
- Push notifications / webhooks on status change (parcels are pull-only for now).
- Multi-tenant auth — no API keys or user scoping yet.
- Bulk registration endpoint.

## Open questions
- None outstanding; assumptions above (e.g. list doesn't refresh) were made explicitly to
  keep courier API usage bounded and predictable at ~1k parcels/month.
