# Terraform

How the AWS infrastructure is written, checked and, when the time comes, deployed. What it builds and why is in the [infra plan](infra-plan.md); the cost-driven design is [ADR 0002](adr/0002-low-cost-production-architecture.md).

The infrastructure is **ready but off**: the code is complete and checked by CI on every change, but nothing runs in AWS until the maintainer decides to deploy. Everything below, except the first deploy, works without an AWS account.

## Layout

```
infra/
├── .tflint.hcl         # lint rules for every stack
└── bootstrap/          # the state bucket, applied once by hand
    ├── state.tf
    ├── backend.tf.example  # backend block, once the bucket exists
    ├── backend.hcl.example # bucket and region, kept out of git
    └── tests/          # terraform test, against a mocked provider
```

Each directory under `infra/` with its own state is a **stack**. `bootstrap` comes first and is separate because it creates the bucket that holds every stack's state, its own included.

## The toolchain

Terraform and TFLint come from the [Nix flake](../flake.nix): the default shell (`nix develop`) has them with the rest of the toolchain, and the `infra` shell (`nix develop .#infra`) has only them, for CI.

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
| Validation | `terraform -chdir=bootstrap init -backend=false`, then `validate` | Syntax, types, references, provider schema |
| Lint | `tflint --recursive` | Deprecated syntax, unused declarations, missing types and versions |
| Tests | `terraform -chdir=bootstrap test` | The properties that must hold, below |

`init -backend=false` downloads the providers but never touches a backend, so it needs no bucket and no credentials. It writes `.terraform/`, which is git-ignored and takes about 770 MB for the AWS provider (safe to delete; the next `init` downloads it again), and `.terraform.lock.hcl`.

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
- **Values a test needs are overridden.** `override_data` gives `aws_caller_identity` a fixed account ID, so the bucket name can be asserted.
- **Data sources are mocked too.** The bucket policy is built with `jsonencode` instead of the `aws_iam_policy_document` data source, whose JSON a mock would make up.
- **`command = plan`.** A `run` applies by default, and the test then destroys what it applied; `prevent_destroy` would make that cleanup fail. Plans are enough to read the configuration.

| Test file | What it proves |
|---|---|
| `bootstrap/tests/state.tftest.hcl` | Bucket named per account, versioned, encrypted, never public, TLS only; old versions expire but the newest are kept; retention must be positive; a project name S3 would reject fails at plan time |

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

The remaining steps (applying `envs/prod`, pushing the backend image) arrive with the modules of Phase 2.

### On another machine

`backend.hcl` is not in git, so a fresh clone recreates it before its first `init`: copy `backend.hcl.example` and fill in the bucket, `itacatl-tfstate-<account ID>` (`aws s3 ls | grep tfstate` finds it), then `terraform init -backend-config=backend.hcl`.

### Removing the state bucket

The ephemeral deploys destroy `envs/prod` only; the bootstrap stays, since its bucket costs cents a month and keeps every stack's history. To remove it anyway, after destroying every other stack:

1. Move the bootstrap's state back to local: delete `backend.tf` and run `terraform init -migrate-state`.
2. Remove `prevent_destroy` from `state.tf`, and empty the bucket, every version included (S3 refuses to delete a bucket that is not empty).
3. `terraform destroy`.

## Extending

- **A new stack:** a directory under `infra/` with `versions.tf` pinned like the bootstrap's, a `backend "s3"` block with its own `key`, a `tests/` directory, and a block of its own in the `Terraform` job of `ci.yml`.
- **A new property worth guarding** (a port that must stay closed, a resource that must stay private): an `assert` in that stack's tests, so a later change cannot quietly undo it.
- **Upgrading the AWS provider:** raise the constraint in `versions.tf`, run `terraform -chdir=bootstrap init -upgrade -backend=false` and commit the updated `.terraform.lock.hcl`. The lock holds the provider's published checksums (`zh:`) for every platform, plus a hash of the unpacked package (`h1:`) for each platform that has run `init`; the first `init` on another platform, a Mac for instance, adds its own `h1:` line, which is committed like any other change to the lock.
- **Upgrading Terraform:** the version and the four hashes in `nix/terraform.nix`, and `required_version` if the minor changes.
