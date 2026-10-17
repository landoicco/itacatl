# CLAUDE.md

## Project

Portfolio project **itacatl**. Currently a simple e-commerce CRUD (Spring Boot + React + PostgreSQL), being reworked into a multi-tenant marketplace where small businesses such as *fondas* publish dynamic menus and customers reserve food for pickup. It is built to be deployed to AWS with Terraform.

Read these before planning any feature or infrastructure work:

- `docs/product-spec.md`: product scope, domain rules, order flow.
- `docs/infra-plan.md`: AWS architecture, cost rules, work phases.

The goal is learning: every technical choice should be one the maintainer can defend in an interview. Explain trade-offs and prefer well-known, production-grade patterns over clever shortcuts.

## Layout

- `backend/`: Spring Boot 4 (Java 21), Maven, Flyway, JPA. Package root `dev.licaza.itacatl`.
- `frontend/`: React 19, TypeScript, Vite, Tailwind, Vitest.
- `docker-compose.yml` + `.env` (git-ignored, copied from `.env.example`): local full stack (database, backend, frontend).
- `docs/`: specs, plans, and ADRs in `docs/adr/`.

## Environment

The toolchain is defined in `flake.nix` and entered with `nix develop`.

- When the project needs a tool (a language, a CLI, a database client), add it to the `tools` list in `flake.nix` instead of installing it globally, and say which one you added.
- Prefer packages from `nixpkgs`. Look a name up with `nix search nixpkgs <name>` before adding it.
- After changing `flake.nix`, the shell must be re-entered (`exit`, then `nix develop`) for the tool to appear; tell the user when that is needed.
- `flake.lock` pins the versions: commit it together with `flake.nix`.

## Commands

Backend (from `backend/`):

- `mvn verify`: build and run all tests. `spotless:check` runs in the `compile` phase, so unformatted code fails the build.
- `mvn spotless:apply`: format Java (google-java-format) and `pom.xml`.
- `mvn test -Dtest=ClassName`: run a single test class.

Frontend (from `frontend/`, run `npm ci` first):

- `npm run lint`, `npm run build`, `npm test -- --run`.
- `npm run format`: Prettier.

Full stack: `docker compose up --build`, app at `http://localhost:3000`.

A PostToolUse hook (`.claude/hooks/format.sh`) formats every file Claude edits with Spotless or Prettier. It needs the Nix shell and `frontend/node_modules`.

## Conventions

- **Language:** the maintainer chats in Spanish, but everything committed (code, comments, docs, commit messages, PR descriptions) is in English.
- **Docs in every PR:** each PR updates the docs it affects (README, `docs/`, ADRs), so `main` and its documentation never drift apart. A mechanism that gets added (a library, a pattern, a safety net) gets a guide in `docs/` explaining why it is there, how it is wired in and how to extend it. Ideas that were dropped are not documented.
- **Internationalization:** no user-facing text is hardcoded. UI strings go to the locale catalogs (`frontend/src/i18n/locales/`, every key in `en` and `es-MX`), API errors are codes the client translates, and formatting is locale-aware. See `docs/i18n.md`.
- **Spring first:** prefer Spring's own tools (Security, Session, Data, Validation, Scheduling) over managed services, unless the service provides something Spring can't.
- **Flyway:** until the first production deploy, migrations may be squashed into a clean baseline (ADR 0003); from then on, never edit an applied migration and add a new `V{n}__description.sql`.
- **Language of content:** sample data and seed files are in English by default; Spanish content is a per-deployment setting (e.g. the `es-MX` seed file), and Spanish domain terms get an English gloss: ice cream shops (*neverías*). See `docs/domain-model.md`.
- **Time:** never call `now()` directly; inject the `Clock` bean. Schedules are local times plus the business's zone ID.
- **Backend layering:** controller → service interface → implementation → repository, with DTOs at the API boundary. Keep that structure.
- **Frontend layering:** `app/` → `features/<name>/` → shared layers (`components/`, `api/`, `theme/`, `i18n/`). A feature never imports another feature; ESLint enforces it. Components use theme tokens (`bg-surface`, `rounded-card`, `text-display`), never raw colors or fonts. See `docs/architecture.md` and `docs/theming.md`.
- **Vertical-agnostic naming:** code, API and branding name things generically (`items`, `featuredItems`, `business`), never after the demo vertical (`flavors`, `scoops`).
- **Comments:** explain *why*, not *what*; match the density of the surrounding code.
- **Configuration per environment:** Spring profiles `local` (docker compose) and `aws` (prod), activated with `SPRING_PROFILES_ACTIVE`. The same image runs everywhere, so don't use Maven profiles for environment differences.
- **Logging:** SLF4J with `{}` placeholders, never `System.out`. Business events start with an emoji: 📦 data import, ⏭️ skipped record, ✅ success, 📉 out of stock, 💳 payment, 🔒 concurrency conflict, ❌ failure. Locally logs are colored text; the `aws` profile emits ECS JSON for CloudWatch.

## Working rules

- **Never run `git`, `gh`, `aws` or `terraform` commands**, read-only ones included. Suggest the exact command and let the maintainer run it, or ask explicitly before running one. Commits are always made by the maintainer. `.claude/settings.json` enforces this with `ask`/`deny` rules.
- **This repository is public.** Never commit secrets, credentials, AWS account IDs, personal data, or real phone numbers. Use placeholders and `.env.example`-style files.
- **Cost rules are non-negotiable.** Any infrastructure change must follow the cost rules in `docs/infra-plan.md` (the target customers are small businesses). Flag anything that could create recurring AWS charges before writing it.
