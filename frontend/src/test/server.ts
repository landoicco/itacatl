import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import type {
  BusinessDetail,
  BusinessSummary,
  CatalogItem,
  CurrentUser,
  ItemRequest,
  MenuDetail,
} from "@/api/types";

/*
 * A fake API for component tests. MSW intercepts the real fetch calls, so tests exercise the
 * client, the TanStack Query hooks and the components together, like the browser would.
 * Data follows the English seed, with the clock at Saturday 17 October 2026, 11:30 in
 * Mexico City (see NOW).
 */

export const NOW = new Date("2026-10-17T17:30:00Z");
const ZONE = "America/Mexico_City";

export const shops: BusinessSummary[] = [
  {
    slug: "sunny-scoops",
    name: "Sunny Scoops",
    description: "Family ice cream shop on the main square since 1987.",
    timeZone: ZONE,
    openingStatus: { state: "OPEN", closesAt: "2026-10-17T21:00:00", opensAt: null },
    featuredItems: ["Chocolate ice cream", "Lime sorbet", "Passion fruit sorbet", "Rompope"],
  },
  {
    slug: "the-frozen-corner",
    name: "The Frozen Corner",
    description: "Small-batch ice cream with traditional Mexican flavors.",
    timeZone: ZONE,
    openingStatus: { state: "CLOSED", closesAt: null, opensAt: "2026-10-17T12:00:00" },
    featuredItems: ["Vanilla ice cream"],
  },
];

const everyDay = (
  ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const
).map((dayOfWeek) => ({ dayOfWeek, opensAt: "11:00:00", closesAt: "21:00:00" }));

export const sunnyDetail: BusinessDetail = {
  ...shops[0],
  acceptsPickup: true,
  acceptsDineIn: true,
  weeklyHours: everyDay,
  upcomingMenus: [
    { id: 1, serviceDate: "2026-10-17", startsAt: "11:00:00", endsAt: "21:00:00" },
    { id: 2, serviceDate: "2026-10-18", startsAt: "11:00:00", endsAt: "21:00:00" },
  ],
};

const menu = (id: number, serviceDate: string, items: MenuDetail["items"]): MenuDetail => ({
  id,
  businessSlug: "sunny-scoops",
  businessName: "Sunny Scoops",
  serviceDate,
  startsAt: "11:00:00",
  endsAt: "21:00:00",
  items,
});

export const menus: Record<number, MenuDetail> = {
  1: menu(1, "2026-10-17", [
    {
      id: 11,
      name: "Lime sorbet",
      description: "Tart and refreshing.",
      price: 40,
      available: 40,
      alcoholic: false,
    },
    {
      id: 12,
      name: "Passion fruit sorbet",
      description: null,
      price: 45,
      available: 3,
      alcoholic: false,
    },
    {
      id: 13,
      name: "Rompope",
      description: "Eggnog liqueur.",
      price: 60,
      available: 0,
      alcoholic: true,
    },
  ]),
  2: menu(2, "2026-10-18", [
    {
      id: 21,
      name: "Coconut ice cream",
      description: null,
      price: 45,
      available: 20,
      alcoholic: false,
    },
  ]),
};

const problem = (status: number, code: string) =>
  HttpResponse.json(
    { status, detail: code, code },
    { status, headers: { "Content-Type": "application/problem+json" } }
  );
const notFound = (code: string) => problem(404, code);

// The demo accounts of the seed: Ana owns two shops, Luis one. Sofia, only in tests, works at
// Sunny Scoops as staff, for the read-only screens
export const PASSWORD = "itacatl-demo";
export const accounts: Record<string, CurrentUser> = {
  "ana@example.com": {
    email: "ana@example.com",
    name: "Ana",
    businesses: [
      { slug: "hilltop-paletas", name: "Hilltop Paletas", role: "OWNER" },
      { slug: "sunny-scoops", name: "Sunny Scoops", role: "OWNER" },
    ],
  },
  "luis@example.com": {
    email: "luis@example.com",
    name: "Luis",
    businesses: [{ slug: "the-frozen-corner", name: "The Frozen Corner", role: "OWNER" }],
  },
  "sofia@example.com": {
    email: "sofia@example.com",
    name: "Sofia",
    businesses: [{ slug: "sunny-scoops", name: "Sunny Scoops", role: "STAFF" }],
  },
};

const SUNNY_CATALOG: CatalogItem[] = [
  {
    id: 101,
    name: "Lime sorbet",
    description: null,
    suggestedPrice: 40,
    alcoholic: false,
    archived: false,
  },
  {
    id: 102,
    name: "Maracuyá sorbet",
    description: null,
    suggestedPrice: 45,
    alcoholic: false,
    archived: false,
  },
  {
    id: 103,
    name: "Rompope",
    description: "Eggnog liqueur.",
    suggestedPrice: 60,
    alcoholic: true,
    archived: false,
  },
  {
    id: 104,
    name: "Old flavor",
    description: null,
    suggestedPrice: 30,
    alcoholic: false,
    archived: true,
  },
];

// The fake server's state: who is signed in, and what the management API has changed. Every test
// starts signed out with the original data (setup.ts calls resetServerState after each one)
let session: CurrentUser | null = null;
let catalog: CatalogItem[] = [];

export const signInAs = (email: string | null) => {
  session = email ? accounts[email] : null;
};

export function resetServerState() {
  session = null;
  catalog = SUNNY_CATALOG.map((item) => ({ ...item }));
}
resetServerState();

// The management API's checks, in the backend's order: a session, then the role in the business
function denied(slug: unknown, manage: boolean) {
  if (!session) return problem(401, "AUTHENTICATION_REQUIRED");
  const role = session.businesses.find((business) => business.slug === slug)?.role;
  if (!role || (manage && role !== "OWNER")) return problem(403, "ACCESS_DENIED");
  return null;
}

// NotBlank, the one constraint of the item form that the browser does not check
function invalidItem(item: ItemRequest) {
  return item.name.trim()
    ? null
    : HttpResponse.json(
        { status: 400, code: "VALIDATION_FAILED", errors: [{ field: "name", code: "NotBlank" }] },
        { status: 400, headers: { "Content-Type": "application/problem+json" } }
      );
}

const ITEMS = "/api/manage/businesses/:slug/items";
const catalogOf = (slug: unknown) => (slug === "sunny-scoops" ? catalog : []);

function changeItem(id: unknown, change: Partial<CatalogItem>) {
  const item = catalog.find((candidate) => candidate.id === Number(id));
  if (!item) return notFound("ITEM_NOT_FOUND");
  Object.assign(item, change);
  return HttpResponse.json(item);
}

export const handlers = [
  http.get("/api/auth/me", () =>
    session ? HttpResponse.json(session) : problem(401, "AUTHENTICATION_REQUIRED")
  ),
  http.post("/api/auth/login", async ({ request }) => {
    const { email, password } = (await request.json()) as { email: string; password: string };
    if (!(email in accounts) || password !== PASSWORD) {
      return problem(401, "INVALID_CREDENTIALS");
    }
    signInAs(email);
    return HttpResponse.json(session);
  }),
  http.post("/api/auth/logout", () => {
    signInAs(null);
    return new HttpResponse(null, { status: 204 });
  }),
  http.get(
    ITEMS,
    ({ params }) => denied(params.slug, false) ?? HttpResponse.json(catalogOf(params.slug))
  ),
  http.post(ITEMS, async ({ params, request }) => {
    const body = (await request.json()) as ItemRequest;
    const rejected = denied(params.slug, true) ?? invalidItem(body);
    if (rejected) return rejected;
    const item = { ...body, id: Math.max(...catalog.map(({ id }) => id)) + 1, archived: false };
    catalog.push(item);
    return HttpResponse.json(item, { status: 201 });
  }),
  http.put(`${ITEMS}/:id`, async ({ params, request }) => {
    const body = (await request.json()) as ItemRequest;
    return denied(params.slug, true) ?? invalidItem(body) ?? changeItem(params.id, body);
  }),
  http.post(
    `${ITEMS}/:id/archive`,
    ({ params }) => denied(params.slug, true) ?? changeItem(params.id, { archived: true })
  ),
  http.post(
    `${ITEMS}/:id/unarchive`,
    ({ params }) => denied(params.slug, true) ?? changeItem(params.id, { archived: false })
  ),
  http.get("/api/businesses", () => HttpResponse.json(shops)),
  http.get("/api/businesses/:slug", ({ params }) =>
    params.slug === "sunny-scoops" ? HttpResponse.json(sunnyDetail) : notFound("BUSINESS_NOT_FOUND")
  ),
  http.get("/api/menus/:id", ({ params }) => {
    const found = menus[Number(params.id)];
    return found ? HttpResponse.json(found) : notFound("MENU_NOT_FOUND");
  }),
];

export const server = setupServer(...handlers);
