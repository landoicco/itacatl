# Terraform

How the AWS infrastructure is written, checked and, when the time comes, deployed. What it builds and why is in the [infra plan](infra-plan.md); the cost-driven design is [ADR 0002](adr/0002-low-cost-production-architecture.md).

The infrastructure is **ready but off**: the code is complete and checked by CI on every change, but nothing runs in AWS until the maintainer decides to deploy. Everything below, except the first deploy, works without an AWS account.

## Layout

```
infra/
├── .tflint.hcl         # lint rules for every directory
├── .terraform-docs.yml # how each module's README tables are generated
├── bootstrap/          # stack: the state bucket, applied once by hand
│   ├── state.tf
│   ├── backend.tf.example  # backend block, once the bucket exists
│   ├── backend.hcl.example # bucket and region, kept out of git
│   └── tests/          # terraform test, against a mocked provider
├── modules/            # building blocks, no state of their own
│   ├── network/        # VPC, subnets, routes
│   │   ├── README.md   # what it is for, plus generated inputs and outputs
│   │   └── tests/
│   ├── database/       # RDS PostgreSQL
│   ├── backend/        # ECR, the spot Auto Scaling group, its IAM role and Elastic IP
│   └── frontend/       # S3 and CloudFront; ACM and Route 53 when a domain is given
├── testing/
│   └── aws/            # values for the mocked AWS provider, shared by the test files
└── envs/
    └── prod/           # stack: every module wired together
        ├── main.tf
        ├── backend.hcl.example
        └── tests/
```

Each directory with its own state is a **stack**. `bootstrap` comes first and is separate because it creates the bucket that holds every stack's state, its own included. `envs/prod` is the application's environment: it holds no resources of its own, only the modules and the values that join them (the network's subnets passed to the database, for instance).

A **module** is a reusable piece with inputs and outputs and no state; a stack instantiates it. The modules join `envs/prod` one per PR, in the order of the [infra plan](infra-plan.md#phase-2-modules), so the stack always plans as a whole.

## The toolchain

Terraform, TFLint, terraform-docs and Trivy come from the [Nix flake](../flake.nix): the default shell (`nix develop`) has them with the rest of the toolchain, and the `infra` shell (`nix develop .#infra`) has only them, for CI.

- **One copy of the AWS provider.** Every directory that runs `init` (each stack and each module, for its tests) would download its own copy of the AWS provider, about 770 MB unpacked: six modules and two stacks would take 6 GB of identical files. Both shells set `TF_PLUGIN_CACHE_DIR` to `~/.cache/terraform/plugins`, so the provider is downloaded once and each `.terraform/` only links to it. The lock files still check its checksums, so the cache cannot slip in a different binary. In CI the job downloads the provider once instead of once per directory. Two caveats: a `.terraform/` initialized before the cache existed keeps its own full copy until it is deleted and `init` runs again; and Terraform does not make the cache safe for concurrent use, so `init` runs in one directory at a time, never in parallel loops or terminals.

- **Terraform is HashiCorp's release binary**, defined in `nix/terraform.nix`: the zip from `releases.hashicorp.com`, pinned by version and by the SHA-256 HashiCorp publishes for each platform. nixpkgs also packages Terraform, but the public Nix cache never builds unfree packages, so every machine would compile it from source. Upgrading means changing the version and copying the four hashes from that release's `SHA256SUMS`.
- **Its license is declared.** Since 1.6 Terraform is under the Business Source License, which Nix counts as unfree. `nix/terraform.nix` says so (`meta.license`), and the flake allows that one package by name (`allowUnfreePredicate`), so accepting it is a visible decision. The BSL only restricts offering a product that competes with HashiCorp's.
- **Versions are pinned in three places:** the Terraform binary by `nix/terraform.nix`, TFLint by `flake.lock`, the Terraform versions the code accepts by `required_version` (`~> 1.17`), and the AWS provider by `.terraform.lock.hcl`, which is committed. CI uses the same flake, so it runs the same binaries.

## State

Terraform's state is its record of what exists. It lives in an S3 bucket, `itacatl-tfstate-<account ID>`, one key per stack.

| Setting | Why |
|---|---|
| Name with the account ID | Bucket names are global; the ID makes it unique without writing it in the repository |
| Versioning; an old version expires after 90 days, but the newest 10 are always kept | A corrupted or wrong state can be rolled back, even in a stack nobody applied for months |
| Encryption with SSE-S3 | State holds resource attributes, some sensitive. A customer managed KMS key would add $1/month and no protection that matters here |
| Public access blocked, ACLs disabled, TLS required | State must only be reachable by the account's own principals |
| `prevent_destroy` | Deleting the bucket would lose the record of every stack. It outlives the ephemeral deploys, at a few cents a month ([removing it](#removing-the-state-bucket)) |
| `use_lockfile` | Locks the state with a file next to it in S3, so two applies cannot run at once. Terraform 1.10 made the DynamoDB lock table unnecessary |

The bucket name contains the account ID, which stays out of this public repository, so every stack's backend gets the bucket and its region from a `backend.hcl` file that git ignores (`terraform init -backend-config=backend.hcl`). `backend.hcl.example` shows its shape.

## Checks without AWS

The `Terraform` job in CI runs these on every pull request, through the flake's `infra` shell, with no AWS credentials ([CI](ci.md#ci)). Locally:

| Check | Command (from `infra/`) | Catches |
|---|---|---|
| Format | `terraform fmt -check -recursive` | Unformatted code |
| Validation | `terraform -chdir=<dir> init -backend=false`, then `validate` | Syntax, types, references, provider schema |
| Lint | `tflint --recursive` | Deprecated syntax, unused declarations, missing types and versions |
| Module docs | `terraform-docs --output-check .` | A module README whose inputs and outputs tables no longer match the code (`terraform-docs .` regenerates them) |
| Security scan | `trivy config --exit-code 1 .` | Misconfigurations, of every severity: a public resource, a missing encryption, an open port ([accepted findings](#accepted-trivy-findings)) |
| Tests | `terraform -chdir=<dir> test` | The properties that must hold, below |

`<dir>` is each stack and each module: `bootstrap`, `modules/network`, `modules/database`, `modules/backend`, `modules/frontend`, `modules/observability`, `envs/prod`. `init -backend=false` downloads the providers but never touches a backend, so it needs no bucket and no credentials. It writes `.terraform/`, which is git-ignored and only links to the shared plugin cache, and `.terraform.lock.hcl`, which is committed in every directory, modules included, so their tests run against the same provider version as CI.

### Tests with a mocked provider

`terraform test` runs the files in each stack's `tests/`. With `mock_provider "aws"` (Terraform 1.7 and later) the AWS provider is replaced by a fake that returns made-up values, so a test plans the real code without an account:

```hcl
mock_provider "aws" {}

run "plain_http_is_denied" {
  command = plan

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_s3_bucket_policy.state.policy).Statement :
      statement.Effect == "Deny" && statement.Condition.Bool["aws:SecureTransport"] == "false"
    ])
    error_message = "The bucket policy must deny requests without TLS."
  }
}
```

- **They test intent, not AWS.** A mock cannot tell whether AWS would accept a setting; it proves that the code asks for what it should: a private, versioned, encrypted bucket. `validate` covers the provider's schema.
- **Values a test needs are overridden.** `override_data` gives `aws_caller_identity` a fixed account ID, so the bucket name can be asserted. Where the code builds ARNs, the provider rejects one made of the mock's random strings, so `infra/testing/aws/aws.tfmock.hcl` fixes the partition, region and account with `mock_data`, and the ARN of each resource that another one takes as an argument, such as a CloudFront function, with `mock_resource`. A test file loads it with `mock_provider "aws" { source = "../../testing/aws" }`, so every file uses the same values and a change is made once. `override_resource` fills in what AWS would create on its own and a mock does not, such as the master secret RDS makes for the database.
- **Data sources are mocked too.** Policies are built with `jsonencode` instead of the `aws_iam_policy_document` data source, whose JSON a mock would make up.
- **`command = plan`.** A `run` applies by default, and the test then destroys what it applied; `prevent_destroy` would make that cleanup fail. Plans are enough to read the configuration. The exception is a check that compares IDs, such as which route table a subnet uses: they are unknown in a plan, so that `run` applies against the mock, which creates nothing, in a module without `prevent_destroy`.

| Test file | What it proves |
|---|---|
| `bootstrap/tests/state.tftest.hcl` | Bucket named per account, versioned, encrypted, never public, TLS only; old versions expire but the newest are kept; retention must be positive; a project name S3 would reject fails at plan time |
| `modules/network/tests/network.tftest.hcl` | Public and private subnets in two zones with non-overlapping ranges; each subnet uses its own route table, and only the public one has a route, to the internet gateway; no subnet hands out public IPs; the default security group allows nothing; DNS is on; one zone, more zones than the region has, a VPC smaller than /20, an IPv6 range or one with host bits set is rejected |
| `modules/database/tests/database.tftest.hcl` | The database is private, single-AZ and only in the subnets it is given; storage encrypted, seven days of backups, deletion protection on, and a destroy leaves a final snapshot with a unique name, also when the protection is lifted; RDS manages the master password and IAM sign-in is on; TLS is required, with a parameter group matching the engine; its security group has no rules; one subnet, or backups turned off, is rejected |
| `modules/backend/tests/backend.tftest.hcl` | One spot instance, never on-demand, chosen by price and capacity, with capacity rebalancing, of any of the types in any of the subnets; a launch hook holds each new instance until it reports ready, and abandons it otherwise; IMDSv2 with two hops, an encrypted root volume, the latest arm64 AMI resolved at launch, standard CPU credits; only CloudFront reaches it, on 8080; it reaches only the database and HTTPS, and the database admits it; every permission names its resource, `rds-db:connect` only as `itacatl_app`, only the master secret, metrics only to its own namespace, health reports only to its own group; the user data runs the image with the `aws` profile, takes the Elastic IP and reports to the hook, starts the CloudWatch agent with no host dimension and the disk aggregated by path, and the watchdog on liveness; the group publishes its instances in service; immutable, scanned images, ten tagged ones kept, never counted with untagged ones; logs kept 14 days; one subnet, one instance type or an x86 type is rejected |
| `modules/frontend/tests/frontend.tftest.hcl` | The bucket is named per account, blocks public access, has ACLs off, and is read only by this distribution, over TLS; the app is served from it over HTTPS, read-only, with the routes rewritten to `index.html`, in the cheapest price class; `/api/*` goes to the backend's address on its port, uncached, with every method and the viewer's cookies; without a domain, no certificate or DNS records and CloudFront's own certificate; with one, a DNS-validated certificate, A and AAAA aliases and TLS 1.2 or later; a domain outside `us-east-1`, or a name too long for the bucket, is rejected |
| `modules/observability/tests/observability.tftest.hcl` | Within CloudWatch's free tier: ten alarms at most, none high-resolution; an alarm for the backend's group, memory and disk, CloudFront's 5xx rate and the database's CPU, billed surplus CPU and storage; a group that stops reporting counts as empty; every alarm emails the topic when it fires and when it clears; a graph per alarm; an invalid email, or a region other than `us-east-1`, is rejected |
| `envs/prod/tests/prod.tftest.hcl` | How prod wires the modules: they are named `<project>-prod`; the database sits in the network's private subnets and the backend in its public ones; CloudFront's `/api` origin is the backend's Elastic IP, and prod is served on CloudFront's domain without a domain of its own; every alarm watches this environment's backend, database or distribution; a project name S3 would reject fails at plan time |

Each module tests its own properties; a stack's tests only check how it joins the modules, so a property is asserted once.

### Accepted Trivy findings

Trivy reports every severity, and CI fails on any finding. One that is accepted on purpose is ignored inline, in a `#trivy:ignore:<ID> <reason>` comment right above the resource, so the reason sits next to the code and a new finding still fails. They all come from the [cost rules](infra-plan.md#cost-rules):

| Finding | Where | Why it is accepted |
|---|---|---|
| `AVD-AWS-0089`, S3 access logging | `bootstrap`, the state bucket | It needs a second bucket; only Terraform, run by the maintainer, reads this one |
| `AVD-AWS-0132`, S3 without a customer-managed key | `bootstrap`, the state bucket | A key bills $1/month and protects nothing more here: only the account's principals can read the bucket |
| `AVD-AWS-0178`, VPC without flow logs | `network` | CloudWatch bills each GB they ingest, and at this traffic they show little the security groups do not already prevent |
| `AVD-AWS-0133`, RDS without Performance Insights | `database` | The basic RDS metrics, which have no charge, are what the alarms need |
| `AVD-AWS-0033`, ECR without a customer-managed key | `backend` | A key bills monthly; ECR encrypts the images with its own |
| `AVD-AWS-0017`, a log group without a customer-managed key | `backend` | The same; CloudWatch encrypts the logs with its own |
| `AVD-AWS-0089`, `AVD-AWS-0090`, `AVD-AWS-0132`: S3 access logging, versioning, a customer-managed key | `frontend`, the build's bucket | CloudFront is its only reader; every file can be rebuilt from its commit; a key bills monthly |
| `AVD-AWS-0010`, CloudFront access logs | `frontend` | They bill for their storage; the alarms use CloudFront's basic metrics, which have no charge |
| `AVD-AWS-0011`, CloudFront without WAF | `frontend` | WAF bills monthly; rate limiting is an [open decision](infra-plan.md#open-decisions) |
| `AVD-AWS-0095`, an SNS topic without encryption | `observability`, the alarms' topic | CloudWatch can only publish to a topic encrypted with a customer-managed key, which bills monthly; the alarms carry no secrets |
| `AVD-AWS-0104`, egress to any address | `backend` | HTTPS to AWS's public endpoints, whose addresses are not fixed; without a NAT gateway or VPC endpoints they are reached over the internet |

Accepting a new one takes a reason that names what it would cost or why it does not apply, and a row here.

## Modules

- **Versions:** a module declares only a lower bound for each provider (`>= 6.73` for AWS); the stack that uses it pins the version (`~> 6.73`) and its lock decides the exact one. Besides AWS, `database` uses `hashicorp/random`, which runs locally and needs no credentials, so the tests use the real one.
- **README:** what the module is for and why it is built that way, written by hand, followed by the inputs and outputs tables that `terraform-docs` generates between the `BEGIN_TF_DOCS` and `END_TF_DOCS` markers. The tables come from each variable's and output's `description`, so those are written for a reader of the README.
- **Tests:** each module has its own `tests/`, with a mocked provider like the stacks.

| Module | What it creates |
|---|---|
| [`network`](../infra/modules/network/README.md) | VPC over two zones; public subnets for the backend, private ones for the database with no route out; no NAT gateway; the default security group emptied |
| [`database`](../infra/modules/database/README.md) | RDS PostgreSQL 18, single-AZ, in the private subnets; master password in Secrets Manager, IAM sign-in for the application, TLS required; encrypted, backed up, protected from deletion, with a final snapshot; a security group that clients add their rules to |
| [`frontend`](../infra/modules/frontend/README.md) | A private S3 bucket for the build; one CloudFront distribution for the app and `/api`, with the app's routes rewritten to `index.html` and security headers; with a domain, its ACM certificate and Route 53 aliases |
| [`backend`](../infra/modules/backend/README.md) | ECR; one spot Graviton instance in an Auto Scaling group, in the public subnets, in service only once the app answers; an Elastic IP as CloudFront's origin; a security group open only to CloudFront, and the database's rule for it; an IAM role with one resource per permission; the image tag in SSM; logs in CloudWatch, memory and disk through the CloudWatch agent; a watchdog that has a hung app's instance replaced |
| [`observability`](../infra/modules/observability/README.md) | Seven CloudWatch alarms (the backend's group, memory and disk; CloudFront's 5xx rate; the database's CPU, billed surplus CPU and storage), emailed through SNS when they fire and when they clear; one dashboard; all within CloudWatch's free tier |

Each module's README ends with what to confirm on the first apply: what a mocked provider cannot check against AWS.

## First deploy

Nothing in AWS is created until this runbook is followed. It runs from the maintainer's machine, with their own credentials: CI never gets access to AWS.

1. **Credentials.** Add `awscli2` to the flake and sign in, preferably with IAM Identity Center (`aws configure sso`, then `aws sso login`).
2. **Create the state bucket**, with a local state for now:
   ```shell
   cd infra/bootstrap
   terraform init
   terraform apply
   ```
3. **Move the bootstrap's own state into the bucket:**
   ```shell
   printf 'bucket = "%s"\nregion = "%s"\n' \
     "$(terraform output -raw state_bucket)" "$(terraform output -raw region)" > backend.hcl
   cp backend.tf.example backend.tf
   terraform init -migrate-state -backend-config=backend.hcl
   ```
   Answer `yes` when Terraform offers to copy the state.
4. **Check the copy before deleting the local one.** The local file is the only record of the bucket until the copy is confirmed:
   ```shell
   terraform state list    # now read from S3: must list aws_s3_bucket.state and its settings
   rm -f terraform.tfstate terraform.tfstate.backup
   ```
   From then on the bootstrap reads its state from S3 like any stack. Commit `backend.tf`, which holds no account ID; `backend.hcl` stays out of git.

5. **Apply prod**, with the same bucket and region:
   ```shell
   cd ../envs/prod
   cp ../../bootstrap/backend.hcl backend.hcl
   cp terraform.tfvars.example terraform.tfvars   # git-ignored; fill it in
   terraform init -backend-config=backend.hcl
   terraform apply
   ```
   `terraform.tfvars` takes the address the alarms are emailed to, which is required, and, optionally, the business's own domain, whose hosted zone must already exist in Route 53. Without a domain, the marketplace is served on CloudFront's own `*.cloudfront.net` domain.

   The database takes about ten minutes, the CloudFront distribution several. The backend's instance boots too, but its image tag is still `none`, so it goes into service without the app until the first deploy; that is expected.

   **Confirm the alarms' email:** AWS sends the address a link, and no alarm arrives until it is followed. An unconfirmed subscription expires after three days; a new `terraform apply` creates it again.
6. **Deploy the backend**, as in [Deploying the backend](#deploying-the-backend).
7. **Deploy the frontend**, as in [Deploying the frontend](#deploying-the-frontend).
8. **Smoke test**, through CloudFront, back in `infra/envs/prod`:
   ```shell
   cd ../infra/envs/prod   # from frontend/, where step 7 ends
   url=$(terraform output -raw url)
   curl -fsS "$url/" | grep -q '<div id="root">' && echo "app ok"
   curl -fsS "$url/api/businesses" && echo "api ok"
   ```
   Then open `$url` in a browser, open a shop, and sign in to the owner panel: the session and CSRF cookies only work if CloudFront forwards them.
9. **Check the alarms**, still in `infra/envs/prod`:
   ```shell
   aws cloudwatch describe-alarms --alarm-name-prefix itacatl-prod- \
     --query 'MetricAlarms[].[AlarmName,StateValue]' --output table
   ```
   All of them should be `OK`, or `INSUFFICIENT_DATA` for a metric with no data yet. Then open the `$(terraform output -raw dashboard_name)` dashboard in the CloudWatch console: each graph should have a line.

### Deploying the backend

A deploy pushes an image, records its tag in SSM, and replaces the instance, which reads the tag when it boots. From `infra/envs/prod`, on a clean checkout of the commit to deploy:

```shell
repo=$(terraform output -raw backend_repository_url)
tag=$(git rev-parse --short HEAD)
aws ecr get-login-password | docker login --username AWS --password-stdin "${repo%%/*}"
docker buildx build --platform linux/arm64 --provenance=false --tag "$repo:$tag" --push ../../../backend

aws ssm put-parameter --overwrite \
  --name "$(terraform output -raw backend_image_tag_parameter_name)" --value "$tag"
aws autoscaling start-instance-refresh \
  --auto-scaling-group-name "$(terraform output -raw backend_autoscaling_group_name)"
```

- **The image is arm64**, the Graviton instances' architecture. The Dockerfile compiles on the machine's own platform, so on an x86 machine only the final stage runs under emulation; Docker needs QEMU registered for arm64 (`docker run --privileged --rm tonistiigi/binfmt --install arm64`, or `boot.binfmt.emulatedSystems` on NixOS).
- **One manifest per image.** `--provenance=false` skips the build attestation, which buildx would push as an extra untagged manifest; ECR's retention counts tagged images apart from untagged ones anyway.
- **Tags are immutable**: pushing the same commit twice fails, so a rebuild of the same code needs a new commit.
- **A few minutes without the backend.** The refresh terminates the instance and launches another, which pulls the image, migrates and takes the Elastic IP once `/actuator/health` answers; only then does it report ready to the group's launch hook and go into service. An image that never becomes healthy is abandoned and retried on a new instance about every ten minutes, until a working tag is deployed. `aws autoscaling describe-instance-refreshes --auto-scaling-group-name <name>` shows its progress; the app's logs are in the `/itacatl-prod/backend` log group.
- **Rolling back** is the same two commands with an earlier tag still in ECR, which keeps the last ten. A migration is not rolled back, so a tag older than the database's schema may not start.
- **Inside the instance:** Session Manager opens a shell (`aws ssm start-session --target <instance ID>`, with the Session Manager plugin). The boot's output is in `/var/log/cloud-init-output.log`.

### Deploying the frontend

The build goes to the bucket; CloudFront serves it. From `frontend/`, on a clean checkout of the commit to deploy:

```shell
npm ci
VITE_DEFAULT_LOCALE=es-MX VITE_THEME=pastel npm run build   # the deployment's locale and theme

bucket=$(terraform -chdir=../infra/envs/prod output -raw frontend_bucket_name)
aws s3 sync dist "s3://$bucket" --exclude index.html \
  --cache-control "public, max-age=31536000, immutable"
aws s3 cp dist/index.html "s3://$bucket/index.html" --cache-control no-cache
aws s3 sync dist "s3://$bucket" --delete --exclude index.html \
  --cache-control "public, max-age=31536000, immutable"
```

- **The locale and theme are build settings** ([i18n](i18n.md), [theming](theming.md)), like the backend's seed file: each deployment builds with its own.
- **Three steps, in this order.** The new files go up first, then the `index.html` that names them, and only then are the previous build's files deleted. A visitor who loaded the old `index.html` a moment before still finds the files it names.
- **No invalidation needed.** The other files carry a content hash in their names, so a new build never reuses a name and they can be cached for a year. `index.html` is uploaded with `no-cache`, so CloudFront checks it on every request and a deploy shows at once. If it ever has to be forced: `aws cloudfront create-invalidation --distribution-id "$(terraform -chdir=../infra/envs/prod output -raw frontend_distribution_id)" --paths /index.html`.

### Destroying prod

The database refuses to be deleted, so a destroy takes two steps, from `infra/envs/prod`:

```shell
terraform apply -var database_deletion_protection=false   # only lifts the protection
terraform destroy
```

The destroy also deletes the ECR repository and the frontend's bucket with what they hold, which every commit can rebuild. The apply is needed because the protection is enforced by AWS, not by Terraform: a destroy only plans deletions, and RDS refuses to delete an instance that still has it on. Once the apply has turned it off, the destroy goes through. RDS then takes a final snapshot, named `itacatl-prod-final-<random suffix>`, and deletes the instance; its automated backups go with it. The snapshot stays, billed by the size of the data (cents for a small database), and the next database never restores from it on its own. List and delete old ones when they are no longer needed:

```shell
aws rds describe-db-snapshots --snapshot-type manual --query 'DBSnapshots[].DBSnapshotIdentifier'
aws rds delete-db-snapshot --db-snapshot-identifier <identifier>
```

### On another machine

`backend.hcl` is not in git, so a fresh clone recreates it before its first `init`: copy `backend.hcl.example` and fill in the bucket, `itacatl-tfstate-<account ID>` (`aws s3 ls | grep tfstate` finds it), then `terraform init -backend-config=backend.hcl`.

### Removing the state bucket

The ephemeral deploys destroy `envs/prod` only; the bootstrap stays, since its bucket costs cents a month and keeps every stack's history. To remove it anyway, after destroying every other stack:

1. Move the bootstrap's state back to local: delete `backend.tf` and run `terraform init -migrate-state`.
2. Remove `prevent_destroy` from `state.tf`, and empty the bucket, every version included (S3 refuses to delete a bucket that is not empty).
3. `terraform destroy`.

## Extending

- **A new stack:** a directory under `infra/` with `versions.tf` pinned like the bootstrap's, a `backend "s3"` block with its own `key`, a `backend.hcl.example`, a `tests/` directory, and a block of its own in the `Terraform` job of `ci.yml`.
- **A new module:** a directory under `modules/` with `versions.tf` (lower bound only), a `README.md` with the `terraform-docs` markers, a `tests/` directory (loading the shared mock from `testing/aws` if it builds ARNs), and a block of its own in the `Terraform` job. Then wire it in `envs/prod` and add a test there for how it is joined.
- **A new property worth guarding** (a port that must stay closed, a resource that must stay private): an `assert` in that stack's tests, so a later change cannot quietly undo it.
- **Upgrading the AWS provider:** raise the constraint in every `versions.tf` (`~>` in the stacks, `>=` in the modules), run `terraform -chdir=<dir> init -upgrade -backend=false` in each directory and commit the updated `.terraform.lock.hcl` files. The lock holds the provider's published checksums (`zh:`) for every platform, plus a hash of the unpacked package (`h1:`) for each platform that has run `init`; the first `init` on another platform, a Mac for instance, adds its own `h1:` line, which is committed like any other change to the lock.
- **Upgrading Terraform:** the version and the four hashes in `nix/terraform.nix`, and `required_version` if the minor changes.
