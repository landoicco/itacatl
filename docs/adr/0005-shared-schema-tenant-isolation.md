# 5. One shared schema, isolated by Hibernate and Row-Level Security

- **Status:** Accepted
- **Date:** 2026-10-31

## Context

Every business on the marketplace is a tenant whose catalog, menus and schedule must stay its own. The target customers are small food businesses: many tenants, each with little data, all served by one small PostgreSQL instance on RDS ([ADR 0002](0002-low-cost-production-architecture.md)). Since slice 1a every tenant-owned table has a `tenant_id` column with composite foreign keys, and since slice 2b the services look every ID up together with its business. Both depend on code being written correctly every time: one query that forgets the tenant exposes another business's data.

The public marketplace must still read across tenants: the home page lists every business with its next menu.

## Decision

- **A shared schema with a discriminator column** (`tenant_id`), kept from slice 1a.
- **Hibernate's `@TenantId`** on every tenant-owned entity, fed by a thread-bound tenant scope that each request opens from its URL before its transaction. With no scope, tenant-owned queries find nothing (fail closed). The public marketplace runs in Hibernate's root tenant, which skips the filter (slice 3a).
- **PostgreSQL Row-Level Security** under it, with the tenant passed to each pooled connection and a runtime database role that cannot bypass the policies. The public scope may only read, and cannot see draft menus (slice 3b).
- **Open Session in View off**, so a Hibernate session opens with the transaction, after the tenant is known.

Rejected:

- **A schema per tenant.** Strong separation and easy per-tenant backups, but every migration runs once per schema, the number of tables grows with every business signing up, and the public marketplace would need a query per schema, or a union across them, to list businesses. It fits a few large tenants, not many small ones.
- **A database per tenant.** The strongest isolation, and a cost per business, which the cost rules exclude.
- **Scoped queries alone** (`findByIdAndTenantId` everywhere). Kept as a layer, but not enough on its own: nothing stops the next query from forgetting the tenant.
- **Hibernate filters (`@Filter`) enabled by hand.** The mechanism `@TenantId` is built on, but they must be enabled on every session, they do not apply to loads by ID, and they do not fill the tenant on insert.

## Consequences

- A tenant-owned query cannot leave its business by accident, and a code path that forgets to open a scope sees no tenant data instead of all of it.
- Code that runs outside a request (the seed, scheduled jobs in slice 4) must open a scope before calling a transactional service, and a transaction cannot switch tenants.
- Cross-tenant reads are explicit and listed in one place, the URL rules of `TenantConfig`.
- Native SQL bypasses Hibernate's filter until Row-Level Security covers it.
- How it works: [tenant isolation guide](../tenant-isolation.md).
