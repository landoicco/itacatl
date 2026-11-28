# Project status

Where the work stands, what comes next, and what is still undecided. It is the entry point for anyone picking the project up, a contributor or an AI assistant on a fresh machine. It points to the guides instead of repeating them, and every PR updates it.

## Now

- **Released:** [v0.2.0](https://github.com/landoicco/itacatl/releases), tenant isolation (slices 1 to 3 of the [rework roadmap](product-spec.md#rework-roadmap)).
- **Infrastructure:** Phase 1 (bootstrap) of the [infra plan](infra-plan.md#work-phases) is done, and so are Phase 2's modules, one PR each, except `notifications`. Nothing is deployed: the infrastructure is ready but off ([Terraform guide](terraform.md)), and must stay under a hard ceiling of $30/month when running, with the database never cut back to save money ([cost rules](infra-plan.md#cost-rules)).
- **Latest:** both open decisions settled and built: a secret origin header, so only this CloudFront distribution reaches the backend ([ADR 0006](adr/0006-origin-secret-header.md)), and sign-in rate limiting per address and per email with Bucket4j ([security](security.md#sign-in-rate-limiting)). Before them, the `observability` module (seven CloudWatch alarms and one dashboard, within the free tier, with the CloudWatch agent and a watchdog in the `backend` module), the `frontend` module (S3 and one CloudFront distribution, the domain an optional deployment setting), the `backend` module with the `aws` profile's IAM sign-in to RDS, Trivy in CI, the `database` and `network` modules and the `envs/prod` stack.
- **Goal:** the infrastructure is finished when it is [deploy-ready](infra-plan.md#definition-of-done), with nothing created in AWS until the maintainer decides to launch.
- **Next**, in this order:
  1. Phase 3: Infracost and the arm64 image build in CI.
  2. Closing the runbook: the first-apply checks of every module gathered in one place, and the list of account prerequisites created by hand (hosted zone, AWS Budgets, the maintainer's sign-in).
  3. `notifications` waits until slices 5 and 6 need it.

  Slice 4 of the roadmap (cash reservations) comes after, or in between if the maintainer prefers.

## Open decisions

Not settled yet; each needs the maintainer's call before the work that depends on it.

- **None open.** The infra plan's last two, rate limiting and the hop from CloudFront to the backend, are settled ([infra plan](infra-plan.md#open-decisions)).

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
| The arm64 image, the one that runs in AWS, is not built in CI | Phase 3 of the [infra plan](infra-plan.md#phase-3-ci) |
| The slug is resolved more than once per management request ([tenant isolation](tenant-isolation.md#known-limitations)) | Only if it shows up in profiling |

## Parked ideas

Neither planned nor dropped: they are kept here so later designs don't close the door on them. An idea that is dropped is removed.

- **Catalog mode for physical stores.** A hardware or stationery store publishes its catalog, with stock on the item instead of on a dated menu; customers leave an order and pay when they pick it up. A second offer mode chosen per vertical, next to dynamic menus. Large catalogs would need search, categories, pagination and bulk import. Slice 4 should not tie orders so tightly to dated menus that this needs a rewrite.
- **Package-by-module backend** with Spring Modulith.
- **An optional color per item**, for the UI.
- **Per-vertical copy** in the locale catalogs ([i18n](i18n.md#known-limitations)).
- **A flow video in the README**, once more screens exist.
- **More observability, once real traffic exists**, each within CloudWatch's free ten metrics and ten alarms ([observability module](../infra/modules/observability/README.md)):
  - A metric filter that counts the app's `ERROR` logs (ECS JSON), with an alarm when they spike: one custom metric.
  - An alarm on the backend's `CPUSurplusCreditsCharged`, in case a type ignores the standard credit mode.
  - A few application metrics through Micrometer (JVM heap, HTTP latency, connection pool), filtered to two or three, since each costs about $0.30 a month.
  - Database Insights in its free Standard mode, if `db.t4g.micro` supports it.
- **Edge protection through CloudFront's free flat-rate plan**, which includes WAF with IP rate limiting and caps the bill with no overage charges. To confirm before relying on it: whether the plan can be managed with Terraform, and whether it allows a custom origin header ([security](security.md#sign-in-rate-limiting)).
- **TLS on the hop from CloudFront to the backend**, once a deployment has its own domain: a certificate for an origin name, kept across spot replacements ([ADR 0006](adr/0006-origin-secret-header.md)).
