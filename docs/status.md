# Project status

Where the work stands, what comes next, and what is still undecided. It is the entry point for anyone picking the project up, a contributor or an AI assistant on a fresh machine. It points to the guides instead of repeating them, and every PR updates it.

## Now

- **Released:** [v0.2.0](https://github.com/landoicco/itacatl/releases), tenant isolation (slices 1 to 3 of the [rework roadmap](product-spec.md#rework-roadmap)).
- **Infrastructure:** Phase 1 (bootstrap) of the [infra plan](infra-plan.md#work-phases) is done. Nothing is deployed: the infrastructure is ready but off ([Terraform guide](terraform.md)).
- **Latest:** CI takes its toolchain from the flake ([CI](ci.md#tools-from-the-flake)).
- **Next:** one of
  - Phase 2 of the infra plan: the Terraform modules and `envs/prod`.
  - Slice 4 of the roadmap: cash reservations.

## Open decisions

Not settled yet; each needs the maintainer's call before the work that depends on it.

- **Order of the Terraform modules.** Proposed: `network`, `database`, `backend`, `frontend`, `observability`, then `envs/prod`; `notifications` waits until slices 5 and 6 need it.
- **Infrastructure:** the [open decisions of the infra plan](infra-plan.md#open-decisions) (domain, rate limiting, VPC origins, how `itacatl_app` is created in RDS).

Settled decisions are in the [ADRs](adr/) and in each guide's "why" sections. One without its own guide: the Docker images keep building from their Dockerfiles rather than from Nix, so trying the app locally needs only Docker.

## Deferred follow-ups

Known gaps, each with the work it belongs to.

| Gap | When |
|---|---|
| A 409 response for optimistic locking conflicts | Slice 4, with the stock holds |
| A public write, such as a reservation, gets the `NONE` tenant scope until it is given one on purpose ([tenant isolation](tenant-isolation.md#the-interceptor-and-the-url-rules)) | Slice 4 |
| The owner panel's menu list has no upper bound or history ("see past menus") | When menus pile up |
| Creating a catalog item from the menu editor | Owner panel polish |
| Sign-in rate limiting ([security](security.md#known-limitations)) | Depends on an infra open decision |
| The slug is resolved more than once per management request ([tenant isolation](tenant-isolation.md#known-limitations)) | Only if it shows up in profiling |

## Parked ideas

Neither planned nor dropped: they are kept here so later designs don't close the door on them. An idea that is dropped is removed.

- **Catalog mode for physical stores.** A hardware or stationery store publishes its catalog, with stock on the item instead of on a dated menu; customers leave an order and pay when they pick it up. A second offer mode chosen per vertical, next to dynamic menus. Large catalogs would need search, categories, pagination and bulk import. Slice 4 should not tie orders so tightly to dated menus that this needs a rewrite.
- **Package-by-module backend** with Spring Modulith.
- **An optional color per item**, for the UI.
- **Per-vertical copy** in the locale catalogs ([i18n](i18n.md#known-limitations)).
- **A flow video in the README**, once more screens exist.
