# Project status

Where the work stands, what comes next, and what is still undecided. It is the entry point for anyone picking the project up, a contributor or an AI assistant on a fresh machine. It points to the guides instead of repeating them, and every PR updates it.

## Now

- **Released:** [v0.2.0](https://github.com/landoicco/itacatl/releases), tenant isolation (slices 1 to 3 of the [rework roadmap](product-spec.md#rework-roadmap)).
- **Infrastructure:** Phase 1 (bootstrap) of the [infra plan](infra-plan.md#work-phases) is done, and Phase 2 (modules) is under way, one PR per module. Nothing is deployed: the infrastructure is ready but off ([Terraform guide](terraform.md)), and must stay under a hard ceiling of $30/month when running, with the database never cut back to save money ([cost rules](infra-plan.md#cost-rules)).
- **Latest:** the `frontend` module (a private S3 bucket and one CloudFront distribution for the app and `/api`, the domain an optional deployment setting) and the runbook's frontend deploy. Before it, the `backend` module with the `aws` profile's IAM sign-in to RDS, Trivy in CI, the `database` and `network` modules and the `envs/prod` stack.
- **Goal:** the infrastructure is finished when it is [deploy-ready](infra-plan.md#definition-of-done), with nothing created in AWS until the maintainer decides to launch.
- **Next:** the rest of Phase 2, then Phase 3:
  1. `observability`: alarms and dashboard, the runbook's remaining steps, and whether VPC flow logs, database monitoring and CloudFront logs are worth their cost ([accepted Trivy findings](terraform.md#accepted-trivy-findings)).
  2. Phase 3: Infracost and the arm64 image build in CI.
  3. `notifications` waits until slices 5 and 6 need it.

  Slice 4 of the roadmap (cash reservations) comes after, or in between if the maintainer prefers.

## Open decisions

Not settled yet; each needs the maintainer's call before the work that depends on it.

- **Infrastructure:** the [open decisions of the infra plan](infra-plan.md#open-decisions):
  - Rate limiting: CloudFront's flat-rate plan with WAF, or Bucket4j in Spring.
  - **Encrypting CloudFront's hop to the backend**, which today is plain HTTP with session cookies and sign-in passwords, to a security group that admits any distribution. To settle before launch.

  The production domain is not among them: it is a deployment setting, chosen by whoever launches.

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
| Once in service, the backend's instance is watched only by EC2's status checks: an app that stops answering later is not replaced ([backend module](../infra/modules/backend/README.md)) | `observability` module, with an alarm |
| The arm64 image, the one that runs in AWS, is not built in CI | Phase 3 of the [infra plan](infra-plan.md#phase-3-ci) |
| The slug is resolved more than once per management request ([tenant isolation](tenant-isolation.md#known-limitations)) | Only if it shows up in profiling |

## Parked ideas

Neither planned nor dropped: they are kept here so later designs don't close the door on them. An idea that is dropped is removed.

- **Catalog mode for physical stores.** A hardware or stationery store publishes its catalog, with stock on the item instead of on a dated menu; customers leave an order and pay when they pick it up. A second offer mode chosen per vertical, next to dynamic menus. Large catalogs would need search, categories, pagination and bulk import. Slice 4 should not tie orders so tightly to dated menus that this needs a rewrite.
- **Package-by-module backend** with Spring Modulith.
- **An optional color per item**, for the UI.
- **Per-vertical copy** in the locale catalogs ([i18n](i18n.md#known-limitations)).
- **A flow video in the README**, once more screens exist.
