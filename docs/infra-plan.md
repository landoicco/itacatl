# AWS Infrastructure Plan

## Goal

Make the application deployable to AWS with Terraform in a single environment, `prod`, built to be as cheap as possible. The target customers are small businesses, so the platform has to run at a cost that a marketplace of modest *fondas* can carry.

The infrastructure is **deploy-ready but kept off**: it is validated on every PR (plan, policy checks, cost estimate) and applied only ephemerally to capture evidence, then destroyed. The design is recorded in [ADR 0002](adr/0002-low-cost-production-architecture.md).

The same application image runs locally and in AWS; only configuration changes. Product scope lives in [product-spec.md](product-spec.md).

Assumptions: the account has no credits or 12-month free tier (only *always-free*), the region is `us-east-1`, and the `licaza.dev` hosted zone already exists in Route 53.

## Architecture

```mermaid
flowchart LR
    user([Browser]) -->|"app domain<br/>Route 53 alias, ACM"| cf[CloudFront]
    cf -->|"/* (OAC)"| s3[("S3<br/>frontend build")]
    cf -->|"/api/*<br/>CloudFront prefix list only"| ec2
    subgraph vpc ["VPC: 2 AZs, no NAT"]
        subgraph public [public subnets]
            ec2["EC2 Auto Scaling group<br/>min = max = 1, spot"]
        end
        subgraph private [private subnets]
            rds[("RDS PostgreSQL<br/>single-AZ")]
        end
        ec2 --> rds
    end
    ec2 --> sqs[SQS] --> lambda[Lambda] --> wa([WhatsApp Cloud API])
    sqs -.-> dlq[(DLQ)]
```

- **Backend:** an Auto Scaling group of one instance using a mixed instances policy with spot capacity (`price-capacity-optimized` across several Graviton types) and capacity rebalancing. The ASG replaces the instance when it is reclaimed or fails. A variable switches to on-demand. The instance runs the backend image with Docker; the database never runs on it.
- **Database:** RDS PostgreSQL (PostGIS as an extension), smallest Graviton class, single-AZ, automated backups. The master password is managed by RDS in Secrets Manager.
- **Frontend:** static build in S3, served by CloudFront. Same origin for the app and `/api`, so no CORS in AWS.
- **Notifications:** the backend publishes to SQS; a Lambda outside the VPC sends the WhatsApp messages. Reservations stay synchronous, because they must check stock before confirming.
- **Images:** ECR with a lifecycle policy that keeps the last few tags.
- **Deploys:** the image tag lives in SSM Parameter Store; a deploy updates it and starts an ASG instance refresh. With a single instance this means a few minutes of downtime, accepted and documented.
- **Configuration:** SSM Parameter Store (SecureString for secrets other than the database password).
- **Access:** SSM Session Manager, no SSH.
- **Observability:** CloudWatch Logs with short retention, alarms (instance health, RDS storage and CPU, DLQ depth, CloudFront 5xx) and one dashboard.

## Cost rules

| Rule | Why |
|---|---|
| No NAT gateway | ~$33/month on its own. The EC2 instance sits in a public subnet with a security group that only allows the CloudFront prefix list; RDS needs no internet access |
| No load balancer | ~$17/month. CloudFront terminates TLS and reaches the instance directly |
| No Multi-AZ by default | Doubles the database cost. The ASG and RDS backups cover the failures a small marketplace can tolerate |
| No interface VPC endpoints | ~$7/month each |
| Spot for compute | About a third of the on-demand price |
| Short log retention | 14 days |
| AWS Budgets with alerts | Catches anything left running after an ephemeral apply |

### Estimated monthly cost if left running

Approximate on-demand prices in `us-east-1`, before Infracost validates them:

| Item | USD/month |
|---|---|
| EC2 `t4g.small` spot + EBS | ~6 |
| Public IPv4 address | ~4 |
| RDS `db.t4g.micro` single-AZ + 20 GB | ~14 |
| Secrets Manager (database password) | ~0.40 |
| S3, CloudFront, ECR, SQS, Lambda | ~1 |
| CloudWatch | ~1–3 |
| **Total** | **~25** |

WhatsApp messages are billed by Meta per conversation and are capped by the limits in the product spec.

## Terraform layout

```
infra/
├── bootstrap/          # state bucket, GitHub OIDC, CI roles, Budgets
├── modules/
│   ├── network/        # VPC, public and private subnets, no NAT
│   ├── frontend/       # S3 + CloudFront + OAC + ACM
│   ├── backend/        # ECR, launch template, ASG, IAM, security group
│   ├── database/       # RDS
│   ├── notifications/  # SQS, DLQ, Lambda
│   └── observability/  # alarms, dashboard
├── envs/
│   └── prod/
└── lambdas/            # notification sender
```

- State in S3 with `use_lockfile`.
- `default_tags` on the provider: `Project`, `Env`, `ManagedBy`.

## Repository safeguards

Every change reaches `main` through a PR that passed CI.

**CI** (`.github/workflows/ci.yml`), on PRs to `main` and on pushes to `main`:

| Job | Checks |
|---|---|
| `Backend` | `mvn verify`: Spotless, compilation, tests (Flyway migrations on PostgreSQL via Testcontainers) |
| `Frontend` | `npm ci`, Prettier, ESLint, Vitest, build |
| `Docker images` | Both Dockerfiles build |

The workflow has read-only permissions and actions are pinned to a commit SHA.

**Dependabot** (`.github/dependabot.yml`) checks every week for updates to the GitHub Actions pins, the frontend's npm packages, the backend's Maven dependencies and the base images of both Dockerfiles:

- Minor and patch updates are grouped into one PR per ecosystem, so updates arrive as a few reviewable PRs instead of one per package.
- Security fixes are grouped the same way, and Dependabot opens them as soon as an advisory is published, without waiting for the weekly run.
- Major updates come one per PR, because they can break things and deserve their own review.

Every Dependabot PR goes through the same CI and ruleset as any other change.

**Ruleset on `main`** (configured in GitHub under *Settings → Rules → Rulesets*):

- Restrict deletions and block force pushes.
- Require a pull request before merging, with conversation resolution. Required approvals stay at 0: with a single maintainer, 1 would block every merge.
- Require the status checks `Backend`, `Frontend` and `Docker images`, and require branches to be up to date before merging.
- No bypass list, so the rules apply to the maintainer too.

**Repository security settings** (free for public repositories): secret scanning with push protection, and Dependabot alerts.

## Work phases

The product rework ([product-spec.md](product-spec.md)) runs after Phase 0 and before Phase 4, so the deployment evidence comes from the real domain.

### Phase 0: Prepare the app
- [x] Migrate from MariaDB to PostgreSQL (driver, Flyway migrations, compose)
- [x] App CI on every PR and a ruleset on `main` (see [Repository safeguards](#repository-safeguards))
- [x] Add `.env.example` and remove `.env` from the repo
- [x] Replace `System.out.println` with SLF4J and align on Java 21
- [x] Non-root user in the Dockerfiles; memory limits (JVM and PostgreSQL)
- [x] Frontend: relative API path `/api`
- [x] Rename the project to `itacatl` in a rename-only PR (Java package, Maven, compose, docs), then rename the GitHub repository. It must land before Phase 1, because the GitHub OIDC trust policy contains the repository name.

**Done when:** `docker compose up` works as before and the project is named `itacatl`.

### Phase 1: Bootstrap
- [ ] Add to `flake.nix`: `terraform`, `awscli2`, `tflint`, `trivy`, `terraform-docs`, `infracost`
- [ ] `bootstrap/`: state bucket, OIDC, plan and apply roles, Budgets and SNS
- [ ] Migrate the bootstrap state to S3

**Done when:** remote state exists and Budgets alerts arrive.

### Phase 2: Modules
- [ ] `network`, `database`, `backend`, `frontend`, `notifications` and `observability` modules
- [ ] `envs/prod` wiring them together

**Done when:** `terraform plan` for `envs/prod` is clean and Infracost stays within the estimate above.

### Phase 3: CI/CD
- [ ] `fmt`, `validate`, `tflint`, `trivy` and `terraform test` on PRs
- [ ] `plan` and Infracost comment on PRs
- [ ] Manual `apply` and `destroy` workflows with environment approval; `apply` builds and pushes the image to ECR

### Phase 4: Ephemeral deploy and evidence
- [ ] Apply, seed sample data and run a smoke test
- [ ] Load test with k6 and capture the dashboard
- [ ] Simulate a spot interruption and measure recovery
- [ ] Restore the database from a snapshot
- [ ] Destroy and confirm that nothing is left running

### Phase 5: Documentation
- [ ] README with diagram, cost table and evidence

## Open decisions

- Production domain.
- CloudFront flat-rate free plan (includes WAF) if compatible with an EC2 origin; otherwise rate limiting in Spring with Bucket4j.
- CloudFront VPC origins, which would move the instance to a private subnet and drop the public IPv4 cost, at the price of needing another way out to the internet.
