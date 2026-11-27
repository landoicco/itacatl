# CI and repository safeguards

What every change goes through before it reaches `main`: the CI workflow, the ruleset that requires it, and how dependencies are kept up to date.

## CI

`.github/workflows/ci.yml` runs on pull requests to `main` and on pushes to `main`:

| Job | Checks |
|---|---|
| `Backend` | `mvn verify`: Spotless, compilation, tests (Flyway migrations on PostgreSQL via Testcontainers) |
| `Frontend` | `npm ci`, Prettier, ESLint, Vitest, build |
| `Terraform` | `fmt`, TFLint, terraform-docs, and for each stack and module `init -backend=false`, `validate` and `test` against a mocked AWS provider ([Terraform guide](terraform.md#checks-without-aws)) |
| `Docker images` | Both Dockerfiles build |

The workflow has read-only permissions and actions are pinned to a commit SHA.

### Tools from the flake

The `Backend`, `Frontend` and `Terraform` jobs take their tools from the flake instead of setup actions (`setup-java`, `setup-node`). `cachix/install-nix-action` installs Nix, and each job runs in the flake shell of its part of the repo:

| Shell | Tools | Job |
|---|---|---|
| `.#backend` | JDK 21, Maven | `Backend` |
| `.#frontend` | Node.js 22 | `Frontend` |
| `.#infra` | Terraform, TFLint, terraform-docs | `Terraform` |

**Why:** `flake.lock` already pins every tool for the dev shell. Setup actions would pin them a second time in the workflow, and the two copies drift apart: CI would test with a JDK or Node patch the developers don't use. With the flake, "it passes in `nix develop`" means it passes in CI.

That parity covers the dev shell and CI, not the Docker images: they build on base images that float within Java 21 and Node 22, so production can run a newer patch than CI tested. The `Docker images` job only checks that they build.

**How it is wired in:** each job sets its flake shell as the default `shell` of its steps (`nix develop .#infra -c bash ... {0}`), so each `run:` step stays a plain command and none can forget the shell. In `Backend` and `Frontend` the flake reference is `..#backend` and `..#frontend`, relative to the job's `working-directory`. The three shells are built with `mkShellNoCC`: no step compiles C, and stdenv's compiler and binutils would add hundreds of megabytes to each job's download. Each shell holds only its part's tools, so a job downloads its own toolchain from `cache.nixos.org` and not the others. The default shell, the one `nix develop` enters, is the union of the three plus `gh`.

**Dependency caches:** Nix brings the tools, not what they download. `actions/cache` keeps Maven's local repository (`~/.m2/repository`, keyed on `pom.xml`) and npm's download cache (`~/.npm`, keyed on `package-lock.json`). There are no `restore-keys`: a changed `pom.xml` or lock file starts a fresh cache instead of restoring the previous one and adding to it, so versions no longer used don't pile up. The Nix store itself is not cached: downloading the toolchain from `cache.nixos.org` is fast enough that one more cache action is not worth it.

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

**`flake.lock` is updated by hand**, since Dependabot has no Nix ecosystem. It pins the nixpkgs revision, and with it the patch level of the JDK, Maven, Node and TFLint that the dev shell and CI use. Run `nix flake update` about once a month and whenever a JDK or Node security fix is announced, enter the shell again, and commit the new `flake.lock` in its own PR so CI checks it.

**Majors blocked by the ecosystem** are ignored until their tools catch up, each with its reason next to the rule: TypeScript 7, because `typescript-eslint` 8 declares `typescript <6.1` as its peer and `npm ci` refuses the combination. The rule is removed when `typescript-eslint` supports the new major.
