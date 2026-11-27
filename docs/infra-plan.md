# AWS Infrastructure Plan

## Goal

Make the application deployable to AWS with Terraform in a single environment, `prod`, built to be as cheap as possible. The target customers are small businesses, so the platform has to run at a cost that a marketplace of modest *fondas* can carry.

The infrastructure is **deploy-ready but kept off**: it is checked on every PR without access to AWS (format, lint, security scan, tests against a mocked provider, cost estimate), and applied by the maintainer from their machine only to capture evidence, then destroyed. CI never gets AWS credentials. The design is recorded in [ADR 0002](adr/0002-low-cost-production-architecture.md); how the code is written, checked and deployed, in the [Terraform guide](terraform.md).

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

- **Backend:** an Auto Scaling group of one instance, spot only, using a mixed instances policy (`price-capacity-optimized` across several 2 GB Graviton types, such as `t4g.small`, `c6g.medium` and `c7g.medium`, in both zones) and capacity rebalancing. The ASG replaces the instance when it is reclaimed or fails. There is no on-demand fallback: listing several types and zones makes it rare for none of them to have spot capacity, and when that happens only the backend stops, while the database and its data are unaffected. 2 GB is the smallest size that fits the JVM, Docker and the SSM agent; a 1 GB `t4g.micro` would save about $2/month at the cost of out-of-memory restarts. The instance runs the backend image with Docker; the database never runs on it.
- **Database:** RDS PostgreSQL (PostGIS as an extension), smallest Graviton class, single-AZ. It is the one component kept whole: automated backups, encrypted storage, deletion protection and a final snapshot when it is destroyed, since the data is the only thing the ASG cannot rebuild. The master password is managed by RDS in Secrets Manager. The master user owns the tables and runs Flyway; the backend connects as `itacatl_app`, a role subject to Row-Level Security, which the backend itself creates before migrating ([how the role is created](tenant-isolation.md#how-the-role-is-created)). It signs in with IAM database authentication: a short-lived token from the instance's IAM role, so the runtime holds no database password.
- **Frontend:** static build in S3, served by CloudFront. Same origin for the app and `/api`, so no CORS in AWS.
- **Notifications:** the backend publishes to SQS; a Lambda outside the VPC sends the WhatsApp messages. Reservations stay synchronous, because they must check stock before confirming.
- **Images:** ECR with a lifecycle policy that keeps the last few tags.
- **Deploys:** the image tag lives in SSM Parameter Store; a deploy updates it and starts an ASG instance refresh. With a single instance this means a few minutes of downtime, accepted and documented.
- **Configuration:** SSM Parameter Store (SecureString for secrets other than the database password).
- **Access:** SSM Session Manager, no SSH.
- **Observability:** CloudWatch Logs with short retention, alarms (instance health, RDS storage and CPU, DLQ depth, CloudFront 5xx) and one dashboard.

### Inside the VPC

```mermaid
flowchart TB
    cf[CloudFront] -->|"HTTP, from the CloudFront prefix list only"| ec2
    subgraph vpc ["VPC 10.0.0.0/16 (network)"]
        igw["Internet gateway"]
        subgraph z1 ["Zone 1"]
            subgraph pub1 ["Public subnet 10.0.0.0/24"]
                ec2["Backend instance (backend)<br/>spot, one at a time"]
            end
            subgraph priv1 ["Private subnet 10.0.128.0/24"]
                rds[("RDS PostgreSQL (database)<br/>single-AZ")]
            end
        end
        subgraph z2 ["Zone 2"]
            subgraph pub2 ["Public subnet 10.0.1.0/24"]
                spare["Where the ASG launches<br/>the replacement, if zone 1<br/>has no spot capacity"]
            end
            subgraph priv2 ["Private subnet 10.0.129.0/24"]
                second["Second zone of the RDS<br/>subnet group, which RDS<br/>requires; empty"]
            end
        end
    end
    igw <-->|"public route table: 0.0.0.0/0"| pub1 & pub2
    ec2 -->|"5432, TLS, IAM token<br/>backend SG to database SG"| rds
    ec2 -.->|"ECR, SSM, Secrets Manager,<br/>through the internet gateway"| igw
```

Each box names the module that creates it. The private subnets share a route table with only the local route, so the database has no path to or from the internet; the public ones reach it through the internet gateway. The database's security group admits only the backend's, on port 5432, and the backend's admits only CloudFront. The zones are the region's first two standard ones other than `use1-az3`.

## Cost rules

| Rule | Why |
|---|---|
| **At most $30/month when running** | A hard ceiling for the whole marketplace, not a budget to fill: what a group of small businesses can carry together. The design aims for the cheapest option that stays robust. Pay per use only, with no reservations or savings plans, so destroying the environment brings the bill to cents. Any change that would push the estimate past it is redesigned, not accepted |
| Savings never come from the database | The database holds the only state that cannot be rebuilt, so its backups, encryption, deletion protection and final snapshot are never traded for cost. Compute is where the savings come from |
| No NAT gateway | ~$33/month on its own. The EC2 instance sits in a public subnet with a security group that only allows the CloudFront prefix list; RDS needs no internet access. For the same reason the instance does not move to a private subnet behind CloudFront VPC origins: it would then need a NAT gateway or interface endpoints to reach ECR and SSM |
| No load balancer | ~$17/month. CloudFront terminates TLS and reaches the instance directly |
| No Multi-AZ by default | Doubles the database cost. The ASG and RDS backups cover the failures a small marketplace can tolerate |
| No interface VPC endpoints | ~$7/month each |
| Spot only for compute | About a third of the on-demand price. No on-demand fallback; several instance types and zones keep interruptions short |
| Short log retention | 14 days |
| AWS Budgets with alerts | Catches anything left running after an ephemeral apply. Configured on the account, outside this repository |

### Estimated monthly cost if left running

Approximate prices in `us-east-1` (spot for EC2, on-demand for the rest), before Infracost validates them. Spot prices move, so the ceiling leaves room above the estimate:

| Item | USD/month |
|---|---|
| EC2 2 GB Graviton spot (`t4g.small` or similar) + 8 GB EBS | ~5 |
| Public IPv4 address | ~4 |
| RDS `db.t4g.micro` single-AZ + 20 GB | ~14 |
| Secrets Manager (database master password) | ~0.40 |
| S3, CloudFront, ECR, SQS, Lambda | ~1 |
| CloudWatch | ~1–3 |
| **Total** | **~25–27** |

WhatsApp messages are billed by Meta per conversation and are capped by the limits in the product spec.

## Terraform layout

```
infra/
├── bootstrap/          # state bucket
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
- Every stack and module has `terraform test` files that run against a mocked AWS provider.

## Definition of done

The infrastructure is finished when it is **deploy-ready**: on the day the maintainer decides to launch, the runbook is followed from start to end without designing or writing anything new. Nothing of this project exists in the AWS account before that day, not even the state bucket or a read-only `plan`, so every check below runs without AWS. Small fixes that only a first apply can reveal are accepted after launch: a missing IAM permission, user data details, alarm thresholds, JVM memory, an instance type without spot capacity.

### Each module PR

1. CI passes `fmt`, `tflint`, `validate`, `terraform-docs` and the mocked tests; from the `backend` module on, `trivy` finds nothing that was not accepted on purpose.
2. The module joins `envs/prod`, and the stack's tests check how it is wired to the others.
3. Its cost fits the [estimate](#estimated-monthly-cost-if-left-running).
4. Docs: the module's README, the tests table of the [Terraform guide](terraform.md), its step in the runbook, and what to check about it on the first apply, such as values a mock cannot verify against AWS (instance types, engine versions).
5. The status page is updated.

### Deploy-ready

1. **Complete:** every module except `notifications` is in `envs/prod`. Only a written list of account prerequisites is created by hand: the hosted zone, AWS Budgets and the maintainer's sign-in.
2. **Checked without AWS:** everything above, plus an Infracost estimate within the $30 ceiling in CI.
3. **The application is ready for AWS:** the `aws` profile is complete (database access through IAM, configuration from SSM and Secrets Manager, ECS JSON logs), and the backend image builds for arm64, the architecture of the Graviton instances.
4. **The runbook goes end to end:** from an empty account to a running app with a smoke test, and back to nothing with a destroy, with exact commands. It covers destroying a database under deletion protection and what happens to its final snapshot, and gathers the first-apply checks of every module.
5. **No open decision blocks it:** each one in the [list below](#open-decisions) is settled or deferred on purpose with its known limitation.

The deploy itself and its evidence are Phase 4, outside this definition.

## Work phases

The product rework ([roadmap in the product spec](product-spec.md#rework-roadmap)) runs after Phase 0 and before Phase 4, so the deployment evidence comes from the real domain.

### Phase 0: Prepare the app
- [x] Migrate from MariaDB to PostgreSQL (driver, Flyway migrations, compose)
- [x] App CI on every PR and a ruleset on `main` (see [CI and repository safeguards](ci.md))
- [x] Add `.env.example` and remove `.env` from the repo
- [x] Replace `System.out.println` with SLF4J and align on Java 21
- [x] Non-root user in the Dockerfiles; memory limits (JVM and PostgreSQL)
- [x] Frontend: relative API path `/api`
- [x] Rename the project to `itacatl` in a rename-only PR (Java package, Maven, compose, docs), then rename the GitHub repository.

**Done when:** `docker compose up` works as before and the project is named `itacatl`.

Phases 1 to 3 need no AWS account: each tool joins the Nix shell in the phase that first uses it.

### Phase 1: Bootstrap
- [x] Add to `flake.nix`: `terraform` (HashiCorp's binary) and `tflint`
- [x] `bootstrap/`: the state bucket, with tests against a mocked provider
- [x] [Terraform guide](terraform.md), with the first-deploy runbook

**Done when:** the bootstrap passes `fmt`, `validate`, `tflint` and its tests without AWS credentials.

### Phase 2: Modules
One PR per module, in this order; each joins `envs/prod` in the same PR, so the stack can always be planned as a whole.

- [x] Add `terraform-docs` to `flake.nix`, with a plugin cache shared by every directory
- [x] `network` module, and `envs/prod` wiring it
- [x] `database` module, and the runbook's destroy steps
- [ ] `backend` module; add `trivy` to `flake.nix`, now that there are resources worth scanning
- [ ] `frontend` module
- [ ] `observability` module
- [ ] The application's `aws` profile: database access through IAM, configuration from SSM and Secrets Manager
- [ ] The runbook's remaining steps: applying `envs/prod`, pushing the backend image to ECR, a smoke test, the destroy, and the first-apply checks
- [ ] `notifications` module, postponed until slices 5 and 6 need it

**Done when:** each module PR meets the [definition of done](#each-module-pr).

### Phase 3: CI
- [x] `fmt`, `tflint`, `validate` and `terraform test` on PRs, with no AWS credentials, through the flake's `infra` shell (brought forward to Phase 1)
- [ ] `trivy` on PRs
- [ ] Add `infracost` to `flake.nix` (free API key, kept out of the repository) and run it on PRs
- [ ] Build the backend image for arm64 too

**Done when:** the infrastructure is [deploy-ready](#deploy-ready).

### Phase 4: Ephemeral deploy and evidence
- [ ] Add `awscli2` to `flake.nix`; apply the bootstrap and migrate its state ([runbook](terraform.md#first-deploy))
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