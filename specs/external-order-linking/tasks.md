# Tasks: External order linking

- [x] T1: Add `externalId` to the `Parcel` domain record + carry it through `withX` copies (R1, R3)
- [x] T2: Schema — `external_id` column + `idx_parcels_external_id` index (R1, R2)
- [x] T3: `ParcelStore.findAll` gains an `externalId` filter; implement in Postgres adapter + in-memory fake (R2)
- [x] T4: `PostgresParcelRepository` insert/read/update the `external_id` column (R1, R3)
- [x] T5: Driving port + `ParcelService` — `register` takes `externalId`; `list` takes the filter (R1, R2)
- [x] T6: Web adapter — request/response DTOs gain `externalId`; `GET /parcels?externalId=` filter (R1, R2, R3)
- [x] T7: `openapi.yaml` — request/response schema + query param (R1, R2, R3)
- [x] T8: Tests — service register/list-filter (`ParcelServiceTest`), repository round-trip + filter IT (R1, R2, R3)
