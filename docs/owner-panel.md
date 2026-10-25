# Owner panel

The screens where business owners and staff manage their catalog and menus, under `/manage`. It is the frontend of the [management API](domain-model.md#management-api) and uses the sessions described in the [security guide](security.md). This guide explains how the panel is wired in and why each piece was built the way it is. The layers it follows are in the [architecture guide](architecture.md#frontend).

## Screens

| Route | Screen | Who |
|---|---|---|
| `/login?next=<path>` | Sign-in; afterwards goes to `next`, or `/manage` | Anyone |
| `/manage` | Business picker; skipped when the account has one business | Signed in |
| `/manage/:slug/menus` | Menus from today on, by day, drafts included; owners create, duplicate, publish and delete drafts | Members of the business |
| `/manage/:slug/catalog` | The catalog: search, archived items folded at the end; owners create, edit, archive and unarchive | Members of the business |

The panel is mobile-first: owners update their menu from a phone during the day.

## How it is wired in

| File | Role |
|---|---|
| `api/client.ts` | `request()`: every call to the API, with the CSRF header and error handling. One function per endpoint in `api` |
| `api/types.ts` | The backend's DTOs as TypeScript types |
| `api/errors.ts` | The error codes the UI can translate, and the field errors of a form |
| `api/session.ts` | `useCurrentUser`, `useMembership`, `useSignIn`, `useSignOut` |
| `api/manage.ts` | Queries and mutations of the management API, and what each mutation updates in the cache |
| `api/queries.ts` | Every cache key, public and management |
| `app/routes.tsx` | The routes, and `createQueryClient` with the app-wide error handling |
| `app/RequireSession.tsx` | The guard of `/manage` |
| `app/ManageLayout.tsx` | The frame of one business: header, business switcher, sign-out, sections |
| `components/Sheet.tsx` | The modal sheet used by forms |
| `features/auth/` | The sign-in page |
| `features/account/` | The business picker |
| `features/catalog/` | The catalog page and the item form |
| `features/menus/` | The menu list and the sheet for a menu's date and window |

## Talking to the API

All calls go through `request()` in `api/client.ts`, so the rules below hold for every endpoint, present or future:

- **Same origin.** Paths start with `/api`, which every environment routes to the backend (the Vite proxy, nginx, CloudFront). The session cookie goes along by itself, and there is no CORS to configure.
- **CSRF on every change.** `POST`, `PUT` and `DELETE` carry the `X-XSRF-TOKEN` header, copied from the `XSRF-TOKEN` cookie ([why](security.md#csrf-in-a-single-page-app)). The cookie is read on each request, not once at startup, because the backend replaces the token when someone signs in.
- **Errors become `ApiError`.** A Problem Detail gives its `code` and, for `VALIDATION_FAILED`, its field errors (`fieldErrors`). A response that is not a Problem Detail, such as a proxy's error page, becomes `UNKNOWN_ERROR`, and a request that never reached the server becomes `NETWORK_ERROR`.
- **`204 No Content`** (signing out, deleting a draft) resolves to `undefined` instead of failing to parse an empty body.

### Error messages

The UI never shows the backend's English `detail`. `api/errors.ts` lists the codes that have a message in the `apiErrors` catalog, and `errorCode(error)` returns one of them, or `UNKNOWN_ERROR` for anything else, so a code added to the backend later is never shown raw. Components translate it with ``t(`apiErrors.${errorCode(error)}`)``.

Field errors work the same way: `fieldErrors(error)` maps each field to its Bean Validation constraint (`NotBlank`, `PositiveOrZero`…), translated from the `fieldErrors` catalog. The messages are per constraint, not per field, so a new form needs no new strings for its errors.

## The session

### A query, not a context

Who is signed in is **server state**: the backend owns the session (a cookie and a row in PostgreSQL), and the app only keeps a copy. So it is a TanStack Query query on `GET /api/auth/me`, like any other API data, and not a React context with its own loading and error handling.

- `useCurrentUser()` returns the user, or `null` without a session: `api.getCurrentUser` turns the 401 into `null`, so "signed out" is data, not an error.
- `useSignIn()` writes the user returned by `POST /api/auth/login` into the cache. Every component reading the session re-renders, and the sign-in page redirects.
- `useSignOut()` sets the user to `null` and removes every query under the `manage` key, so nothing of one person's businesses stays in memory on a shared phone.
- `useMembership(slug)` finds the user's role in a business, from the same query.

### The guard

`RequireSession` wraps every `/manage` route. While the session loads it shows placeholders; without a user it redirects to `/login?next=<current path>`; with one it renders the route.

It is a convenience, not a security boundary: the API checks the session and the membership on every call. A tampered client would only see its own 401s and 403s.

**The `next` link only accepts paths of this app.** Following any value would make the sign-in page an *open redirect*: a link to `/login?next=//evil.example` would send someone who has just signed in to another site that looks like this one. `safeNext()` keeps paths that start with a single `/` and falls back to `/manage`.

### When the session ends

A session expires after 12 hours without use, or can be deleted on the server. From then on, every call answers `401 AUTHENTICATION_REQUIRED`. `createQueryClient` registers one handler on the `QueryCache` and the `MutationCache` that sets the user to `null` on that code, and `RequireSession` sends the page to the sign-in form, keeping `next`. No screen has to handle it.

`INVALID_CREDENTIALS` is also a 401, but means a failed sign-in, so the handler ignores it.

### Retries

Queries retry network errors and 5xx twice, and **never a 4xx**: a missing menu, a denied request or a broken rule answer the same on a second try. Mutations never retry, which is TanStack Query's default: repeating a change could apply it twice.

## Keeping the cache current

Every key lives in `queryKeys` (`api/queries.ts`). Management data is under `["manage", slug, …]`, so a business's data, or all of it on sign-out, can be dropped with one prefix.

| Mutation | What it updates |
|---|---|
| Any change to a menu | The backend answers with the whole menu, written into its cache entry with `setQueryData`: no refetch. The menu list is invalidated |
| Any change to the catalog | The catalog and the business's menus, which show each item's name and archived state |
| Both | The public marketplace data (`businesses`, `menus`), since customers may see the change |

Invalidating marks queries as stale; only those on screen refetch at once, the rest when they are next used.

## Forms

Forms are plain React state, without a form library, and the **backend is the source of truth** for validation:

1. **The browser checks first.** Inputs carry the same limits as the request DTOs (`required`, `maxLength={120}`, `min={0}`, `step={0.01}`), so most mistakes are caught before a request is sent.
2. **The server decides.** What the browser cannot check (a name of only spaces fails `NotBlank`) comes back as `VALIDATION_FAILED`, and each message appears under its field.
3. **Errors are announced.** An invalid input gets `aria-invalid` and `aria-describedby` pointing at its message, so a screen reader reads the error with the field. The message sits outside the `<label>`, or it would become part of the input's name.

Inputs are controlled, so the values survive a rejected save. A form is mounted only while its sheet is open, so it starts fresh every time.

## Sheets

`components/Sheet.tsx` is a modal that slides up from the bottom on a phone and is centered on larger screens. It is the native `<dialog>` opened with `showModal()`, which gives, without extra code: focus kept inside the sheet, Escape to close, the top layer above everything, and the page behind it made inert. The sheet tells its parent when it closes by itself (Escape, a click on the backdrop), so the parent's state follows.

## Menus

`MenusPage` lists the menus of `GET …/menus`, which starts at today in the business's time zone. Menus arrive sorted by date and start time and are grouped by day, with "Today" and "Tomorrow" labels.

- **The business's time zone comes from its public profile** (`useBusiness(slug)`, the same query the marketplace uses), because the memberships in the session do not carry it. "Today" is the business's today, not the phone's: an owner travelling in another zone still sees their shop's day.
- **Each card shows what an owner checks at a glance:** the window, Draft or Published, a dot per item, how many are sold out, and whether customers can see it. The window links to the menu's editor.
- **Actions follow the lifecycle** ([domain model](domain-model.md#a-menus-lifecycle)): Publish and Delete only appear on drafts, Duplicate on every menu. Deleting asks for confirmation with the browser's `confirm()`, since it cannot be undone.
- **One sheet for a new menu and a duplicate** (`MenuWindowSheet`): a date and two times. A duplicate is prefilled with the next day (today at the earliest) and the same window, because repeating a menu is the common case. A new menu starts today with empty times, so the owner decides the window.
- **The window's rules are the backend's.** A closing time before the opening time, or a window that has already ended, comes back as `INVALID_TIME_WINDOW` or `MENU_ENDED` and is shown in the sheet, which stays open with the values. Checking it in the browser too would duplicate rules that depend on the business's clock.

The sheet's form takes an `onSubmit` that returns a promise (`mutateAsync`), so one form serves creating and duplicating, and closes only once the save succeeds.

## The panel's frame

`ManageLayout` renders one business's panel:

- **Sections are a tab bar at the bottom on a phone**, where the thumb reaches, and links in the header from the `sm` breakpoint up.
- **The business switcher keeps the section:** from Sunny Scoops' catalog to Hilltop Paletas' catalog.
- **A business the user does not belong to** redirects to `/manage` instead of rendering a page of 403s. The API would refuse every call anyway.
- **Signing out navigates first, then signs out.** The other order would set the user to `null` while the layout is on screen, and `RequireSession` would send the page to the sign-in form instead of the marketplace.

### Owners and staff

Staff see the same screens without the buttons that change anything: `useMembership(slug)?.role === "OWNER"` decides what is rendered. The API enforces the same rule, so hiding a button is about not offering an action that would fail.

## Decisions

| Decision | Instead of | Why |
|---|---|---|
| The session as a TanStack Query query | A React context with `useState` | It is server state: caching, loading and refetching are already solved, and sign-in and sign-out are cache updates |
| One `request()` for every call | `fetch` in each hook | CSRF, errors and empty responses are handled once; an endpoint cannot forget the token |
| The CSRF token read from the cookie on each request | Reading it once, or an endpoint that returns it | The token changes on sign-in; the cookie is always current, and the backend already sends it |
| One app-wide handler for an expired session | Checking for 401 in each screen | A session can end during any call; one place sends the user to sign in |
| No retries for 4xx | TanStack Query's default of three retries | A client error does not change by retrying, and retries delayed the error message by seconds |
| `setQueryData` with the menu each mutation returns | Refetching the menu | The response is the menu; a refetch would be a second request for the same data |
| Plain React forms, validated by the backend | React Hook Form with a schema library (Zod) | The forms are few and small, and duplicating the DTOs' rules in a schema could drift from the backend. HTML attributes catch the common mistakes |
| Native `<dialog>` for sheets | A custom overlay, or a UI library | Focus trapping, Escape and inert background come from the browser, and no dependency is added |
| A tab bar on phones | A hamburger menu | Two sections fit, one tap away, where the thumb reaches |
| Hiding owner-only buttons from staff | Showing them disabled | Staff never need them; the API refuses the calls either way |
| The business's time zone from its public profile | Adding it to the session's memberships | The profile is already a cached query; the session stays small and unchanged |
| Menu window rules checked only by the backend | Repeating them in the form | "Ended" depends on the business's clock and zone; the backend's codes are translated in the sheet |
| `confirm()` before deleting a draft | A custom confirmation sheet | It is the only irreversible action, and the native dialog is accessible and familiar on phones |

## Extending

- **A new management endpoint:** a function in `api` (`api/client.ts`), its types in `api/types.ts`, and a hook in `api/manage.ts` that says what it updates in the cache. If it is a menu change answering with the menu, `useMenuMutation` already does the right thing.
- **A new error code:** add it to `KNOWN_CODES` in `api/errors.ts` and its message to `apiErrors` in every locale. Until then it shows as `UNKNOWN_ERROR`.
- **A new constraint on a request DTO:** add its name to `KNOWN_FIELD_CODES` and a message to `fieldErrors`, and mirror it with an HTML attribute on the input when one exists.
- **A new section of the panel:** a route under `/manage/:slug` in `app/routes.tsx` and its name in `SECTIONS` in `ManageLayout`, with a `manage.sections` string.

## Tests

Pages are tested through the real routes with the API faked by MSW (`test/server.ts`), as described in the [architecture guide](architecture.md#tests). For the panel:

- **The fake server keeps state.** Sign-in sets a session, and catalog and menu changes persist across requests within a test, so a test can create an item or duplicate a menu and then find it in the list. `resetServerState()` runs after each test.
- **It checks like the backend:** 401 without a session, 403 for a business the user does not belong to or a change by staff, `VALIDATION_FAILED` for a blank name, and the menu rules the screens show (`INVALID_TIME_WINDOW`, `MENU_ENDED` for a past date, `MENU_EMPTY`, `MENU_NOT_DRAFT`).
- **Accounts follow the seed** (Ana owns two shops, Luis one), plus Sofia, a staff member who only exists in tests.
- **`renderRoute` uses the app's own `createQueryClient`**, so the expired-session handling runs in tests too; only retries are turned off.

| Test | What it proves |
|---|---|
| `api/client.test.ts` | CSRF header on changes and not on reads, empty 204, field errors, no session as `null` |
| `features/auth/SignInPage.test.tsx` | The redirect to sign-in and back, one message for wrong credentials, the single-business shortcut, a `next` link to another site ignored |
| `features/menus/MenusPage.test.tsx` | Days in the business's zone, publishing and `MENU_EMPTY`, duplicating to the next day, a rule error kept in the sheet, deleting after confirming, staff without actions |
| `features/catalog/CatalogPage.test.tsx` | The catalog, search without accents, creating with a rejected field, editing, archiving; the switcher, the tabs, staff without buttons, a foreign business, an expired session, signing out |

## Gotchas

- **TanStack Query skips `mutate()` callbacks once the component unmounts.** The callbacks given to `useMutation` itself always run. That is why sign-out navigates before signing out instead of in `onSuccess`.
- **jsdom has no `showModal()`.** `test/setup.ts` replaces it with a stub that only sets `open`, which is all the tests need.
- **The first render of a test file is slow** when every file runs in parallel, as in CI, because it also loads the file's modules. `findBy*` waits up to 3 seconds (`asyncUtilTimeout` in `test/setup.ts`) instead of 1.
