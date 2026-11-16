# CI and repository safeguards

What every change goes through before it reaches `main`: the CI workflow, the ruleset that requires it, and how dependencies are kept up to date.

## CI

`.github/workflows/ci.yml` runs on pull requests to `main` and on pushes to `main`:

| Job | Checks |
|---|---|
| `Backend` | `mvn verify`: Spotless, compilation, tests (Flyway migrations on PostgreSQL via Testcontainers) |
| `Frontend` | `npm ci`, Prettier, ESLint, Vitest, build |
| `Terraform` | `fmt`, TFLint, and for each stack `init -backend=false`, `validate` and `test` against a mocked AWS provider ([Terraform guide](terraform.md#checks-without-aws)) |
| `Docker images` | Both Dockerfiles build |

The workflow has read-only permissions and actions are pinned to a commit SHA.

The `Terraform` job takes its tools from the flake instead of setup actions: `cachix/install-nix-action` installs Nix, and `nix develop .#infra` provides the exact Terraform and TFLint of the dev shell, so a version is never pinned twice. The `infra` shell holds only those two, so the job downloads tens of megabytes rather than the whole toolchain. It never gets AWS credentials.

## Ruleset on `main`

Configured in GitHub under *Settings → Rules → Rulesets*:

- Restrict deletions and block force pushes.
- Require a pull request before merging, with conversation resolution. Required approvals stay at 0: with a single maintainer, 1 would block every merge.
- Require the status checks `Backend`, `Frontend`, `Terraform` and `Docker images`, and require branches to be up to date before merging.
- No bypass list, so the rules apply to the maintainer too.

**Repository security settings** (free for public repositories): secret scanning with push protection, and Dependabot alerts.

## Dependabot

`.github/dependabot.yml` checks every week for updates to the GitHub Actions pins, the frontend's npm packages, the backend's Maven dependencies and the base images of both Dockerfiles:

- Minor and patch updates are grouped into one PR per ecosystem, so updates arrive as a few reviewable PRs instead of one per package.
- Security fixes are grouped the same way, and Dependabot opens them as soon as an advisory is published, without waiting for the weekly run.
- Major updates come one per PR, because they can break things and deserve their own review.

Every Dependabot PR goes through the same CI and ruleset as any other change.

**Runtime versions** (Java 21 and Node 22, both LTS) are upgraded by hand, because they must change together in `flake.nix`, the CI workflow, both Dockerfiles and `pom.xml` (`java.version`). Dependabot ignores them: major updates of the base images, the `maven` builder image (its tag embeds the JDK version, so a "minor" tag update can switch Java), and major updates of `@types/node`.

**Majors blocked by the ecosystem** are ignored until their tools catch up, each with its reason next to the rule: TypeScript 7, because `typescript-eslint` 8 declares `typescript <6.1` as its peer and `npm ci` refuses the combination. The rule is removed when `typescript-eslint` supports the new major.
