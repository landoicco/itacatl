# 3. Squash migrations until the first production deploy

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

The marketplace rework replaces the product catalog with a new domain. Adding the new tables as `V4` and dropping the old ones in `V5` would leave the schema's history full of tables that never reached production. No database holds data worth keeping: nothing is deployed, and the local database has no persistent volume.

## Decision

Until the first production deploy, migrations may be rewritten or squashed:

- `V1__create_marketplace_schema.sql` holds the marketplace schema as it should be.
- The legacy product catalog moves to `V2__create_legacy_catalog.sql`, so the current UI keeps working. Slice 1b deletes that file together with the product code.

Keeping migrations append-only now (one new `V{n}` per change) was rejected: it protects databases that do not exist.

## Consequences

- The first deploy starts from a clean, readable baseline instead of a history of abandoned tables.
- Anyone with a local database from before this change must recreate it (`docker compose down`, then `up`). There is no volume, so that already happens on every restart.
- From the first production deploy on, the rule is absolute: an applied migration is never edited, and every change goes in a new `V{n}`.
