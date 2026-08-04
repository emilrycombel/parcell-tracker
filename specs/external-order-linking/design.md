# Design: External order linking

## Requirements coverage

| Requirement | Satisfied by |
|---|---|
| R1 (register with external id) | `Parcel.externalId`, `ParcelService.register`, `ParcelEndpoint.register`, `PostgresParcelRepository.insert`, `schema.sql` |
| R2 (retrieve by external id) | `ParcelStore.findAll(..., externalId)`, `ParcelService.list`, `ParcelEndpoint.list` |
| R3 (expose external id) | `Dtos.ParcelResponse.externalId`, `openapi.yaml` |

## Approach

`externalId` is a nullable `String` on the `Parcel` domain record — an opaque, caller-owned
reference. It's carried through the immutable `withX` copies so a refresh never drops it. It
is **not** part of the (tracking number, courier) uniqueness, so it needs no constraint; a
plain btree index (`idx_parcels_external_id`) backs the lookup filter.

Retrieval reuses the existing list path rather than adding a new endpoint: `GET /parcels`
gains an optional `externalId` query parameter that composes with `status` and pagination.
This keeps the "list never refreshes from the courier" guarantee (R2) for free and avoids a
second code path. A dedicated `GET /parcels/by-external/{id}` was considered and rejected
while an order maps to *many* parcels — a filter returning a page is the honest shape; revisit
if the relationship becomes 1:1 (see requirements Open questions).

## Data model

Adds one nullable column to the existing `parcels` table:

```
external_id  VARCHAR(128)     -- caller's order reference, opaque; nullable; non-unique
```

plus `CREATE INDEX idx_parcels_external_id ON parcels (external_id)`. No new table — the
order itself is not modeled here (out of scope), only the reference.

## API contract

- `RegisterParcelRequest` gains optional `externalId`.
- `Parcel` response schema gains `externalId` (nullable).
- `GET /parcels` gains an optional `externalId` query parameter.

See `META-INF/openapi.yaml`.

## Port impact

- `ParcelStore.findAll` gains an `externalId` filter argument (null = no filter). The in-memory
  fake and the Postgres adapter both implement it.
- The driving port `ParcelTrackingUseCase.register` gains an `externalId` argument; `list`
  gains an `externalId` filter argument.

## Error handling

No new failure modes: `externalId` is free-form and optional. A blank/whitespace value is
normalized to null at the web edge so "" and absent behave identically.
