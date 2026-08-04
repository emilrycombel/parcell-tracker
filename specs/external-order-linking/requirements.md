# Requirements: External order linking

## Overview
Let a caller attach its own order reference (an "external id") to a parcel at registration,
and retrieve all parcels linked to a given order. This lets the main app group parcels under
the order they belong to and display them together. The external id is an opaque string owned
by the caller — this service stores it and filters on it, but never interprets it.

## Requirements

### R1: Register a parcel with an external id
- **WHEN** a client registers a parcel and supplies an `externalId` **THE SYSTEM SHALL**
  persist it on the parcel and echo it back in the response.
- **WHEN** no `externalId` is supplied **THE SYSTEM SHALL** register the parcel with a null
  external id — linking is optional and its absence must not change any other behavior.
- The `externalId` does **not** participate in duplicate detection: the existing
  (tracking number, courier) uniqueness is unchanged, and two parcels may share an
  `externalId` (a single order split across multiple shipments).

### R2: Retrieve parcels by external id
- **WHEN** a client lists parcels filtered by `externalId` **THE SYSTEM SHALL** return only
  the parcels linked to that external id, and **SHALL NOT** trigger a courier refresh (same
  bounded-usage behavior as the existing list).
- The `externalId` filter **SHALL** compose with the existing `status` filter and pagination.

### R3: Expose the external id
- **WHEN** returning a parcel from any endpoint (register, get-by-id, list) **THE SYSTEM
  SHALL** include its `externalId` (or null).

## Out of scope
- Post-hoc linking/unlinking (a PATCH to set/clear `externalId` on an existing parcel) — for
  now the link is set at registration only.
- Storing orders themselves. Orders live in the main app; this service holds only the opaque
  reference, not an order entity.
- Enforcing uniqueness of `externalId` (see R1 — split shipments are allowed).

## Open questions
- None outstanding. The "one order → many parcels" assumption is deliberate; if orders turn
  out to be strictly 1:1 with parcels, add a unique constraint and a dedicated
  `GET /parcels/by-external/{externalId}` returning a single parcel.
