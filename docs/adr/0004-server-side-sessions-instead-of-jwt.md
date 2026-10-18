# 4. Server-side sessions in PostgreSQL instead of JWT

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

Business owners and staff need to sign in to manage their catalog and menus (slice 2). The client is a React app served from the same origin as the API, locally through nginx and in AWS through CloudFront. The backend runs on a single spot instance that can be replaced at any time ([ADR 0002](0002-low-cost-production-architecture.md)), and the cost rules exclude extra paid services.

## Decision

- **Server-side sessions** with Spring Security, carried in an `HttpOnly`, `SameSite=Lax` cookie (`Secure` in AWS).
- **Stored in PostgreSQL with Spring Session JDBC**, so they survive instance replacements and need no Redis. Flyway owns the session tables.
- **CSRF tokens** with the cookie-to-header pattern (`csrf.spa()`), since the browser sends the session cookie automatically.
- **A JSON login endpoint** instead of `formLogin`, so the React app gets JSON and Problem Details instead of redirects.

Rejected:

- **JWT.** A token cannot be revoked before it expires without a server-side denylist, which is a session store again. Keeping it in `localStorage` exposes it to XSS, and short-lived tokens need a refresh flow. Its strength, stateless verification across services, does not apply to one backend.
- **In-memory sessions.** Every spot reclaim or deploy would sign all owners out.
- **Amazon Cognito.** A managed identity service would work, but the project favors Spring's own tools, and Cognito would move the security logic out of the code being showcased.

## Consequences

- Signing a user out, everywhere or on one device, is deleting rows.
- Every authenticated request reads the session from PostgreSQL and updates its last access time: a small, constant load that fits a small marketplace.
- Session attributes are Java-serialized, so the principal class must stay small and stable.
- State-changing requests from the app must send the CSRF header (slice 2c).
- How it works: [security guide](../security.md).
