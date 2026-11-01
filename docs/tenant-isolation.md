# Tenant isolation

How the marketplace keeps each business's data apart while every business shares one database and one schema. The guide starts with the problem and Hibernate's multi-tenancy concepts, then shows how each piece is wired in here, which tenant every part of the application runs as, how to extend it, and the traps to avoid. The decision itself, a shared schema with a discriminator column, is [ADR 0005](adr/0005-shared-schema-tenant-isolation.md). The tables are in the [domain model](domain-model.md), and permissions in the [security guide](security.md).

## The problem

Every business on the marketplace is a **tenant**: its catalog, menus and schedule are its own. All of them live in the same tables, told apart by a `tenant_id` column holding the business's ID:

| `item.id` | `item.tenant_id` | `item.name` |
|---|---|---|
| 1 | 1 (Sunny Scoops) | Mango sorbet |
| 2 | 2 (Cold Corner) | Lime sorbet |

That is cheap and simple to run, but isolation then depends on every query remembering `WHERE tenant_id = ?`. One query that forgets, say `itemRepository.findById(id)` with an ID taken from the URL, lets an owner read or change another business's item by guessing its ID. That bug has a name, **insecure direct object reference (IDOR)**, and it is one of the most common access control flaws in web applications.

So isolation is built in **layers**, each one catching what the one above misses:

| Layer | Where | What it stops | Since |
|---|---|---|---|
| Permission on the business | `@PreAuthorize` with `BusinessAccess` ([security guide](security.md#per-business-permissions-method-security)) | A user acting on a business they are not a member of | Slice 2b |
| Scoped lookups | `findByIdAndTenantId` in the services | An ID of another business in a URL of your own: answered 404 | Slice 2b |
| **Hibernate tenant filter** | `@TenantId` and `TenantContext` (this guide) | A query that forgets the tenant: Hibernate adds it to every query and every insert | Slice 3a |
| Composite foreign keys | `(id, tenant_id)` references in `V1` ([domain model](domain-model.md#tenant-integrity-enforced-by-the-database)) | A row that mixes two businesses, e.g. one business's item in another's menu | Slice 1a |
| PostgreSQL Row-Level Security | Policies on every tenant-owned table | Anything that bypasses Hibernate: native SQL, a bug in this mechanism | Slice 3b (next) |

The first two are code that has to be written correctly every time. The Hibernate filter is written **once** and applies to every query, including those written in the future by someone who has never read this page.

## Hibernate's multi-tenancy, in four concepts

Hibernate supports three ways to separate tenants: a database per tenant, a schema per tenant, or a shared schema with a **discriminator** column. The project uses the third ([why](adr/0005-shared-schema-tenant-isolation.md)), which Hibernate 6 and later support with one annotation.

- **`@TenantId`** marks the discriminator field of an entity. Hibernate then:
  - adds `tenant_id = ?` to every query on that entity: repository methods, JPQL, `findById`, and joins that fetch it;
  - fills the field on insert with the current tenant, and rejects an insert whose field already holds a different tenant.
- **The tenant identifier** is the value bound to that `?`. Here it is the business's ID, a `Long`, the same type as the field.
- **`CurrentTenantIdentifierResolver`** is how Hibernate asks "which tenant?". The application implements it. Hibernate calls it **once, when a session opens**, and keeps the answer for the whole session: a session cannot change tenant halfway.
- **The root tenant.** The resolver's `isRoot(tenantId)` can declare one identifier special: a session opened with it is not filtered, and an insert keeps the tenant the entity already has. It is Hibernate's own hook for code that must work across tenants, such as an admin task or, here, the public marketplace.

Session is Hibernate's word for what JPA calls the `EntityManager`: the unit of work that loads entities and tracks their changes. With Spring, a `@Transactional` method opens one when the transaction begins and closes it at commit.

## How it is wired in

```mermaid
flowchart LR
    req([Request]) --> sec[Spring Security filters]
    sec --> int["TenantScopeInterceptor<br/>opens the scope"]
    int --> ctrl[Controller]
    ctrl --> svc["@Transactional service<br/>opens a Hibernate session"]
    svc -. "asks: which tenant?" .-> res[TenantIdentifierResolver]
    res -. reads .-> ctx[(TenantContext)]
    int -. writes .-> ctx
    svc --> db[(PostgreSQL)]
```

| Piece | Role |
|---|---|
| `tenant/TenantContext` | The current tenant of the thread, set and restored through scopes |
| `tenant/TenantIdentifierResolver` | Hands `TenantContext` to Hibernate, and declares `CROSS_TENANT` the root tenant |
| `tenant/TenantScopeInterceptor` | Opens a request's scope before the controller and closes it when the request completes |
| `tenant/TenantConfig` | Decides, from the URL, which tenant each request gets |
| `@TenantId` on `Item`, `Menu`, `MenuItem`, `BusinessHours`, `ScheduleException` | Marks the discriminator of every tenant-owned entity |
| `spring.jpa.open-in-view=false` | Makes sessions open with the transaction, after the scope (see below) |

### TenantContext: scopes

The current tenant is a `ThreadLocal`, since a servlet request runs on one thread from start to finish. Code never sets it directly: it **opens a scope**, which remembers the previous value and restores it when closed, so scopes nest safely:

```java
try (TenantContext.Scope scope = TenantContext.open(business.getId())) {
  itemRepository.findAll(); // only this business's items
} // the previous tenant is current again
```

The tenant is one of three kinds of value:

| Value | Meaning | Tenant-owned queries | Inserts |
|---|---|---|---|
| A business ID (`> 0`) | Working inside that business | Only its rows | Get that business; another business is rejected |
| `CROSS_TENANT` (`0`) | Hibernate's root tenant: every business at once | Not filtered | Keep the tenant they were given |
| `NONE` (`-1`) | No scope was opened | Find nothing | Rejected |

`NONE` is what makes the design **fail closed**: code that forgets to open a scope sees no business's data, instead of seeing every business's. Tables that belong to no tenant (`business`, `app_user`, `membership`) have no `@TenantId` and work in any scope, which is why sign-in needs no scope at all.

The constraint `ck_business_id CHECK (id > 0)` in `V1` guarantees that `0` and `-1` can never be a real business.

### The resolver

`TenantIdentifierResolver` is three short methods: return `TenantContext.current()`, declare `CROSS_TENANT` the root, and skip a validation that only applies to `getCurrentSession()`, which Spring does not use. It is registered in `application.properties` by class name:

```properties
spring.jpa.properties.hibernate.tenant_identifier_resolver=dev.licaza.itacatl.tenant.TenantIdentifierResolver
```

A Spring bean registered through a `HibernatePropertiesCustomizer` would also work, and is the usual choice when the resolver needs other beans. This one has no dependencies, and the property also reaches test slices such as `@DataJpaTest`, which do not scan components.

### The interceptor and the URL rules

`TenantConfig` maps URLs to tenants in one place:

| URL | Tenant | Why |
|---|---|---|
| `/api/businesses/**`, `/api/menus/**` | `CROSS_TENANT` | The public marketplace lists every business and its published menus |
| `/api/manage/businesses/{slug}/**` | The business with that slug | An owner works on one business at a time |
| Anything else (`/api/auth/**`) | `NONE` | Sign-in and the current user only read accounts and memberships |

For management URLs, the interceptor looks the slug up (`findBySlug`, one indexed query) and opens that business's scope. It does not check permissions: `@PreAuthorize` on the service still decides whether this user may work in that business, and an unknown slug stays `NONE`, so the service answers it as before.

A Spring MVC `HandlerInterceptor` was chosen over a servlet filter because it runs after Spring has matched the URL, so `{slug}` is already parsed. `afterCompletion` runs even when the controller throws, so a failed request never leaves its tenant on a thread that the server reuses for the next request.

### A management request, step by step

```mermaid
sequenceDiagram
    participant B as Browser
    participant F as Security filters
    participant I as TenantScopeInterceptor
    participant S as MenuManagementService
    participant H as Hibernate
    participant DB as PostgreSQL
    B->>F: GET /api/manage/businesses/sunny-scoops/menus/7
    F->>I: signed in (session cookie)
    I->>DB: business with slug sunny-scoops?
    I->>I: TenantContext.open(1)
    I->>S: controller calls getMenu("sunny-scoops", 7)
    S->>S: @PreAuthorize: Ana owns sunny-scoops
    S->>H: @Transactional opens a session
    H->>H: resolver: tenant 1
    S->>H: findByIdAndTenantId(7, 1)
    H->>DB: ... WHERE id = 7 AND tenant_id = 1 AND tenant_id = 1
    S-->>B: 200, or 404 if menu 7 is another business's
    I->>I: afterCompletion: scope closed
```

The tenant appears twice in that query: once from the scoped lookup the service writes, once from the filter. The duplication is deliberate: each layer works without the other.

### Why Open Session in View is off

Spring Boot enables **Open Session in View** (OSIV) by default: a Hibernate session opens when the request arrives and stays open until the response is written, so lazy associations can load while JSON is rendered. Here it would open the session **before** the interceptor sets the tenant, and the whole request would run as `NONE`.

It is off (`spring.jpa.open-in-view=false`), which most production guides recommend anyway: a session per transaction holds a database connection only while the service runs, and a lazy load outside a service fails with `LazyInitializationException` instead of quietly sending queries from the web layer. The services already map entities to DTOs inside their transactions, so nothing depended on it.

## Which tenant each part runs as

| Caller | Scope | Opened by |
|---|---|---|
| Public marketplace endpoints | `CROSS_TENANT` | `TenantConfig` |
| Management endpoints | The business in the URL | `TenantConfig` |
| Sign-in, current user | `NONE` | Nothing: the default |
| Seed at startup | `CROSS_TENANT` | `SeedConfig`, around `MarketplaceSeeder.seed` |
| Tests | `CROSS_TENANT` | `CrossTenantTestExecutionListener` |

The seed creates several businesses in one transaction and sets each row's tenant itself, which the root tenant allows. Slice 3b revisits it, since Row-Level Security will make the cross-tenant scope read-only.

## Extending it

### A new tenant-owned table

1. Add `tenant_id BIGINT NOT NULL REFERENCES business (id)` and, if other tables point to it, `UNIQUE (id, tenant_id)` for their composite foreign keys ([domain model](domain-model.md#tenant-integrity-enforced-by-the-database)).
2. Annotate the entity's field with `@TenantId`.
3. Keep looking rows up with their tenant in the services (`findByIdAndTenantId`), so the 404 for foreign IDs does not depend on the filter alone.
4. Add a case to `TenantIsolationIntegrationTest`.

A table that belongs to no business (accounts, sessions, in the future customers' orders across businesses) gets no `@TenantId`. Decide it explicitly: such a table is visible from every scope.

### A new endpoint

Put it under one of the URL patterns in `TenantConfig`. A management endpoint under `/api/manage/businesses/{slug}/` gets its business's scope without any code. An endpoint outside every pattern runs as `NONE` and sees no tenant data: if it needs some, add a pattern, deliberately, rather than opening a scope inside the controller.

### Work outside a request

A scheduled job (slice 4 expires stock holds with `@Scheduled`) runs on its own thread, with no interceptor, so it starts as `NONE`. It opens a scope per business it works on, **before** calling the transactional service:

```java
for (Long tenantId : tenantIds) {
  try (TenantContext.Scope scope = TenantContext.open(tenantId)) {
    holdService.expireHolds(); // @Transactional: its session opens inside the scope
  }
}
```

### A new cross-tenant read

`CROSS_TENANT` turns the filter off, so the code that runs in it must restrict itself, as the public services do by asking only for `PUBLISHED` menus. Keep it to read-only public data, and prefer adding the endpoint under the public patterns over opening the scope by hand.

## Traps

- **The scope must exist before the transaction.** Hibernate asks for the tenant when the session opens, which is when the `@Transactional` method starts. Opening a scope *inside* a transactional method changes nothing for that session. That is why the scope is opened by the interceptor, by `SeedConfig` around the seeder, and by the test listener before the test transaction.
- **A transaction cannot switch tenant.** A nested `@Transactional` method joins the existing session and its tenant. Only `Propagation.REQUIRES_NEW` opens a new session, which reads the tenant again.
- **New threads start empty.** A `ThreadLocal` is not inherited: `@Async` methods, `CompletableFuture` tasks and executors run as `NONE` unless they open a scope. The same holds for virtual threads.
- **Always close a scope.** Use try-with-resources. A scope left open on a pooled thread would hand its tenant to whichever request that thread serves next.
- **The root tenant trusts the entity.** In `CROSS_TENANT`, an insert keeps the `tenantId` it was given, and one without a tenant fails on `NOT NULL`. Code that runs cross-tenant sets the tenant on every row itself, as the seed and `MarketplaceTestData` do.
- **Native SQL is not filtered.** `JdbcTemplate` and native queries go around Hibernate. Row-Level Security (slice 3b) covers them.
- **Web slice tests load `TenantConfig`.** `@WebMvcTest` includes every `WebMvcConfigurer`, and `TenantConfig` needs `BusinessRepository`, so those tests declare it as a `@MockitoBean`.

## Tests

| Test | What it proves |
|---|---|
| `TenantIsolationIntegrationTest` | In a business's scope, repositories see only its rows, also through `findById` and queries that do not mention the tenant; new rows get that business and rows of another are rejected; `NONE` sees no tenant rows but still sees businesses; `CROSS_TENANT` sees all. Over HTTP, requests that start as `NONE` get the right scope from the interceptor, and leave the thread as they found it |
| `TenantContextTest` | Scopes nest and restore the previous tenant; a scope is invisible to other threads |
| `TenantIntegrityIntegrationTest` | The composite foreign keys reject rows that mix businesses |
| `MenuManagementServiceIntegrationTest`, `CatalogServiceIntegrationTest` | The scoped lookups: another business's ID is a 404 |

`TenantIsolationIntegrationTest` is a full-application test without `@Transactional`, so each repository call opens its own session in the scope current at that moment, as a request does. Every other Spring test runs in `CROSS_TENANT` (`CrossTenantTestExecutionListener`, registered in `src/test/resources/META-INF/spring.factories`): their fixtures build several businesses inside one test transaction, which no single business's scope could do.
