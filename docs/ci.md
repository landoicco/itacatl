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

### Tools from the flake

The `Backend`, `Frontend` and `Terraform` jobs take their tools from the flake instead of setup actions (`setup-java`, `setup-node`). `cachix/install-nix-action` installs Nix, and each job runs in the flake shell of its part of the repo:

| Shell | Tools | Job |
|---|---|---|
| `.#backend` | JDK 21, Maven | `Backend` |
| `.#frontend` | Node.js 22 | `Frontend` |
| `.#infra` | Terraform, TFLint | `Terraform` |

**Why:** `flake.lock` already pins every tool for the dev shell. Setup actions would pin them a second time in the workflow, and the two copies drift apart: CI would test with a JDK or Node patch the developers don't use. With the flake, "it passes in `nix develop`" means it passes in CI.

**How it is wired in:** the `Backend` and `Frontend` jobs set the flake shell as the default `shell` of their steps (`nix develop ..#backend -c bash ... {0}`, the flake reference is relative to the job's `working-directory`), so each `run:` step stays a plain command. The `Terraform` job prefixes each command with `nix develop .#infra -c` instead. Each shell holds only its part's tools, so a job downloads its own toolchain from `cache.nixos.org` and not the others. The default shell, the one `nix develop` enters, is the union of the three plus `gh`.

**Dependency caches:** Nix brings the tools, not what they download. `actions/cache` keeps Maven's local repository (`~/.m2/repository`, keyed on `pom.xml`) and npm's download cache (`~/.npm`, keyed on `package-lock.json`); a missed key falls back to the newest cache of the same tool. The Nix store itself is not cached: downloading the toolchain from `cache.nixos.org` is fast enough that one more cache action is not worth it.

**Adding a tool a job needs:** put it in the list of that part in `flake.nix` (`backendTools`, `frontendTools` or `infraTools`). It then appears in the job's shell and in the dev shell, with no change to the workflow.

None of the jobs gets AWS credentials.

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

**Runtime versions** (Java 21 and Node 22, both LTS) are upgraded by hand, because they must change together in `flake.nix` (which CI uses too), both Dockerfiles and `pom.xml` (`java.version`). Dependabot ignores them: major updates of the base images, the `maven` builder image (its tag embeds the JDK version, so a "minor" tag update can switch Java), and major updates of `@types/node`.

**Majors blocked by the ecosystem** are ignored until their tools catch up, each with its reason next to the rule: TypeScript 7, because `typescript-eslint` 8 declares `typescript <6.1` as its peer and `npm ci` refuses the combination. The rule is removed when `typescript-eslint` supports the new major.
