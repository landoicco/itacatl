# Architecture

How the code is organized, the patterns it uses and why, and the rules that keep it that way as it grows. Infrastructure lives in the [infra plan](infra-plan.md); the domain in the [domain model](domain-model.md).

## A request, end to end

```mermaid
sequenceDiagram
    participant B as Browser (React)
    participant N as nginx / CloudFront
    participant C as Controller
    participant S as Service
    participant R as Repository
    participant DB as PostgreSQL
    B->>N: GET /api/businesses/sunny-scoops
    N->>C: same origin, no CORS
    C->>S: getBusiness(slug)
    S->>R: findBySlug, hours, upcoming menus
    R->>DB: SQL
    DB-->>R: rows
    R-->>S: entities
    S-->>C: BusinessDetailDTO (local times, opening status)
    C-->>B: JSON, or a Problem Detail with a code
```

## Backend

### Layers

```mermaid
flowchart LR
    controller[controller/<br/>HTTP, status codes] --> service[service/<br/>interfaces]
    service --> impl[service/impl/<br/>transactions, use cases]
    impl --> repo[repo/<br/>Spring Data JPA]
    impl --> domain[domain/<br/>entities, pure rules]
    repo --> domain
    controller -.-> dto[dto/<br/>records at the API boundary]
    impl -.-> dto
```

- **Controllers** only map HTTP to service calls. They depend on the service *interface*, so tests replace it with a mock (`@WebMvcTest`).
- **Services** hold the use cases and their transactions (`@Transactional(readOnly = true)` for reads). Their interfaces also carry the permissions of the management API (`@PreAuthorize`), and their implementations the business rules that compare fields or need stored data.
- **Validation** is split by kind: request DTOs declare per-field constraints that `@Valid` checks before the controller runs (400), and services enforce the rules (422). See [validation](domain-model.md#validation-two-kinds-of-no).
- **Repositories** are Spring Data interfaces; queries that need care (fetch joins, time windows) are explicit JPQL.
- **DTOs** are Java records. Entities never reach the JSON, so the API can stay stable while the schema evolves.
- **Errors** become [RFC 9457 Problem Details](domain-model.md#errors) in one place, `GlobalExceptionHandler`, including the 401 and 403 answers of Spring Security's filters.
- **Security** lives in `security/`: the filter chain, how credentials are checked, the signed-in principal and the session cookie. It runs before any controller ([security guide](security.md)).

### Patterns

| Pattern | Where | Why |
|---|---|---|
| Layered architecture | `controller` → `service` → `repo` | One reason to change per layer; each is tested on its own |
| DTO | `dto/` records | Decouples the API from the schema; nothing lazy-loaded leaks into JSON |
| Repository | `repo/` (Spring Data JPA) | Queries behind interfaces; derived queries for the simple ones |
| Value objects | `TimeWindow`, `OpeningStatus` (records) | Immutable, validate themselves (`TimeWindow` rejects overnight windows) |
| Functional core, imperative shell | `WeeklySchedule` (pure) used by `BusinessDatabaseService` (I/O) | The tricky rules are unit-tested with plain dates, no database |
| Static factory methods | `ResourceNotFoundException.business(slug)`, `BusinessRuleException.menuEmpty(id)`, `OpeningStatus.openUntil(…)` | Named constructors that keep codes and states consistent |
| Declarative authorization (proxy) | `@PreAuthorize` on `CatalogService` and `MenuManagementService` | Permissions are part of the service's contract, checked before any caller reaches the method ([security](security.md#per-business-permissions-method-security)) |
| Database as the arbiter | `uq_menu_item` decides a duplicate menu item; `DataIntegrityViolationException` becomes `ITEM_ALREADY_IN_MENU` | A check-then-insert in Java could be passed by two concurrent requests |
| Dependency injection | Constructors everywhere, including `Clock` | Swappable collaborators; a frozen clock in tests |
| Multi-tenancy by discriminator | `tenant_id` + composite foreign keys | One schema for every business; the database rejects cross-tenant rows |
| Optimistic locking | `@Version` on menu items | Concurrent reservations of the last unit cannot both succeed (slice 4) |
| Pessimistic locking of the aggregate root | `SELECT … FOR UPDATE` on the menu before any change to it or its items | Checks and writes on one menu run one after another, so a concurrent `publish` and `removeItem` cannot leave an empty menu public ([why](domain-model.md#concurrent-changes-to-one-menu)) |
| Batch loading | `findByTenantIdIn`, `findByMenuIdInWithItem` | A fixed number of queries per page instead of N+1 |
| Chain of responsibility | Spring Security's filter chain | Each filter handles one concern (session, CSRF, authorization) and passes the request on or stops it |
| Strategy | `PasswordEncoder`, `SessionAuthenticationStrategy` | The hashing algorithm and what happens to the session on sign-in are swappable objects, not code paths |

**Planned:** Ports & Adapters (hexagonal) for notifications and payments, as the [product spec](product-spec.md) describes: a notification port with a WhatsApp adapter, a payment port with gateway adapters.

## Frontend

### Layers

```mermaid
flowchart TD
    app[app/<br/>routes, providers,<br/>panel layout and guard] --> shops[features/shops/]
    app --> menu[features/menu/]
    app --> panel[features/auth/, account/,<br/>catalog/, menus/]
    shops --> shared
    menu --> shared
    panel --> shared
    subgraph shared [shared layers]
        components[components/] --> api[api/]
        components --> theme[theme/]
        components --> i18n[i18n/]
    end
```

| Folder | Holds | May import |
|---|---|---|
| `app/` | Routes, providers, the 404 page, and what frames the owner panel: the session guard and the panel layout | Everything |
| `features/<name>/` | One screen and the components only it uses, with their tests. The marketplace has `shops` and `menu`; the [owner panel](owner-panel.md) has `auth`, `account`, `catalog` and `menus` | Shared layers only, never another feature or `app/` |
| `components/` | UI shared by features: header, opening status, errors, skeletons, the modal sheet | `api/`, `theme/`, `i18n/` |
| `api/` | Typed client, `ApiError` and its translatable codes, TanStack Query hooks: public data, the session and the management API | Nothing above it |
| `theme/` | Tokens, themes, fonts, item colors ([theming](theming.md)) | Nothing above it |
| `i18n/` | Catalogs and `Intl` formatting ([i18n](i18n.md)) | Nothing above it |

This is a **package-by-feature** structure: code that changes together lives together, and a feature can be read, tested or deleted as a unit. (Feature-Sliced Design is a stricter variant with more layers; it would be more ceremony than this app needs.)

### The rules enforce themselves

A convention written only in a document erodes. The dependency rules above are **ESLint errors** (`no-restricted-imports` in `frontend/eslint.config.js`), so CI fails when, for example, `features/menu` imports from `features/shops`:

```
'@/features/shops/ShopCard' import is restricted from being used by a pattern.
A feature must not import another feature or the app. Move shared code to
components/, api/, theme/ or i18n/.
```

### Patterns

| Pattern | Where | Why |
|---|---|---|
| Server state vs UI state | TanStack Query for API data; `useState` only for UI (search text, filters) | Caching, loading and error states, and refetching are solved once, not per `useEffect` |
| Adapter at the boundary | `api/client.ts` | HTTP and Problem Details become typed data and `ApiError` codes; components never see `fetch` |
| Centralized cache keys | `queryKeys` in `api/queries.ts` | Invalidation cannot miss a variant; signing out drops every `manage` key at once |
| Session as server state | `useCurrentUser` (a query), `RequireSession` | Who is signed in is cached like any API data; signing in and out are cache updates ([owner panel](owner-panel.md#the-session)) |
| Serialized mutations | A TanStack Query scope per menu in `api/manage.ts` | Quick taps reach the server and come back in order ([why](owner-panel.md#quick-taps-in-order)) |
| Native platform first | `<dialog>` for sheets, `confirm()` before deleting, HTML constraints on inputs | Focus trapping, Escape and validation come from the browser, with no dependency |
| Design tokens | `theme/themes.css` | Themes per vertical without touching components |
| URL as state | `?menu=<id>` on the shop page | A shared link opens the same service day |
| Composition | `SiteHeader` takes the page's controls as `children` | The header stays generic; each page decides what goes in it |

## Tests

| Level | Backend | Frontend |
|---|---|---|
| Unit | `WeeklyScheduleTest`: pure rules with fixed dates | Formatters, item colors, the API client with a stubbed `fetch` |
| Integration | Services, repositories and the seeder against PostgreSQL in Testcontainers, with a fixed `Clock`; the sign-in flow and the management API over HTTP with the full application (`@SpringBootTest`) and a real sign-in (`BrowserSession`) | Pages through the real routes, with the API faked by **MSW** at the network level. For the owner panel the fake API keeps state and applies the backend's session, role and rule checks |
| Contract | `@WebMvcTest` with the real security rules: JSON shapes, date formats, Problem Details, public access | Catalog parity across locales |

Both sides freeze time in tests: the backend injects a fixed `Clock`, the frontend fakes only `Date` (`vi.useFakeTimers({ toFake: ["Date"] })`) so promises and timers keep working.

Full-application tests (`@SpringBootTest`) do not import `FixedClockConfig`: the application already has a `Clock` bean, so a second one fails the context. They inject the real `Clock` and compute dates from it, like `ManagedMenuControllerIntegrationTest` does for "tomorrow".
