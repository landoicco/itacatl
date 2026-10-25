import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import type {
  AddMenuItemRequest,
  BusinessDetail,
  BusinessSummary,
  CatalogItem,
  CurrentUser,
  ItemRequest,
  ManagedMenu,
  MenuDetail,
  MenuWindow,
  UpdateMenuItemRequest,
} from "@/api/types";
import { todayIn } from "@/i18n/format";

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
let managedMenus: ManagedMenu[] = [];

// Sunny Scoops' menus from today (Saturday 17) on, as the management API lists them
const SUNNY_MENUS: ManagedMenu[] = [
  {
    id: 201,
    serviceDate: "2026-10-17",
    startsAt: "11:00:00",
    endsAt: "21:00:00",
    status: "PUBLISHED",
    items: [
      { itemId: 101, name: "Lime sorbet", price: 40, quantity: 30, archived: false },
      { itemId: 102, name: "Maracuyá sorbet", price: 45, quantity: 0, archived: false },
    ],
  },
  {
    id: 202,
    serviceDate: "2026-10-18",
    startsAt: "12:00:00",
    endsAt: "20:00:00",
    status: "DRAFT",
    items: [{ itemId: 101, name: "Lime sorbet", price: 40, quantity: 20, archived: false }],
  },
  {
    id: 203,
    serviceDate: "2026-10-21",
    startsAt: "12:00:00",
    endsAt: "20:00:00",
    status: "DRAFT",
    items: [],
  },
];

export const signInAs = (email: string | null) => {
  session = email ? accounts[email] : null;
};

export function resetServerState() {
  session = null;
  catalog = SUNNY_CATALOG.map((item) => ({ ...item }));
  managedMenus = structuredClone(SUNNY_MENUS);
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

const MENUS = "/api/manage/businesses/:slug/menus";
const rule = (code: string) => problem(422, code);

// The menu rules that the screens show: a window that ends after it starts and has not ended.
// "Ended" is simplified to a past date; the clock is frozen at 11:30 on the 17th
function invalidWindow({ serviceDate, startsAt, endsAt }: MenuWindow) {
  if (endsAt <= startsAt) return rule("INVALID_TIME_WINDOW");
  if (serviceDate < todayIn(ZONE)) return rule("MENU_ENDED");
  return null;
}

function newMenu(when: MenuWindow, items: ManagedMenu["items"]) {
  const menu: ManagedMenu = {
    id: Math.max(...managedMenus.map(({ id }) => id)) + 1,
    serviceDate: when.serviceDate,
    // The backend answers times with seconds
    startsAt: `${when.startsAt}:00`,
    endsAt: `${when.endsAt}:00`,
    status: "DRAFT",
    items,
  };
  managedMenus.push(menu);
  managedMenus.sort((a, b) =>
    (a.serviceDate + a.startsAt).localeCompare(b.serviceDate + b.startsAt)
  );
  return HttpResponse.json(menu, { status: 201 });
}

const findMenu = (id: unknown) => managedMenus.find((menu) => menu.id === Number(id));

// A change to a menu's items: allowed to owners until the menu has ended. Answers the whole menu
function changeMenu(
  params: { slug?: unknown; id?: unknown },
  change: (menu: ManagedMenu) => Response | void
) {
  const menu = findMenu(params.id);
  if (!menu) return notFound("MENU_NOT_FOUND");
  const rejected =
    denied(params.slug, true) ?? (menu.serviceDate < todayIn(ZONE) ? rule("MENU_ENDED") : null);
  return rejected ?? change(menu) ?? HttpResponse.json(menu);
}

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
  http.get(
    MENUS,
    ({ params }) =>
      denied(params.slug, false) ??
      HttpResponse.json(params.slug === "sunny-scoops" ? managedMenus : [])
  ),
  http.post(MENUS, async ({ params, request }) => {
    const when = (await request.json()) as MenuWindow;
    return denied(params.slug, true) ?? invalidWindow(when) ?? newMenu(when, []);
  }),
  http.post(`${MENUS}/:id/duplicate`, async ({ params, request }) => {
    const when = (await request.json()) as MenuWindow;
    const source = findMenu(params.id);
    if (!source) return notFound("MENU_NOT_FOUND");
    const items = source.items.filter((item) => !item.archived).map((item) => ({ ...item }));
    return denied(params.slug, true) ?? invalidWindow(when) ?? newMenu(when, items);
  }),
  http.post(`${MENUS}/:id/publish`, ({ params }) => {
    const menu = findMenu(params.id);
    if (!menu) return notFound("MENU_NOT_FOUND");
    const rejected =
      denied(params.slug, true) ??
      (menu.status === "DRAFT" && menu.items.length === 0 ? rule("MENU_EMPTY") : null);
    if (rejected) return rejected;
    menu.status = "PUBLISHED";
    return HttpResponse.json(menu);
  }),
  http.delete(`${MENUS}/:id`, ({ params }) => {
    const menu = findMenu(params.id);
    if (!menu) return notFound("MENU_NOT_FOUND");
    const rejected =
      denied(params.slug, true) ?? (menu.status === "DRAFT" ? null : rule("MENU_NOT_DRAFT"));
    if (rejected) return rejected;
    managedMenus = managedMenus.filter(({ id }) => id !== menu.id);
    return new HttpResponse(null, { status: 204 });
  }),
  http.get(`${MENUS}/:id`, ({ params }) => {
    const menu = findMenu(params.id);
    return (
      denied(params.slug, false) ?? (menu ? HttpResponse.json(menu) : notFound("MENU_NOT_FOUND"))
    );
  }),
  http.put(`${MENUS}/:id`, async ({ params, request }) => {
    const when = (await request.json()) as MenuWindow;
    const menu = findMenu(params.id);
    if (!menu) return notFound("MENU_NOT_FOUND");
    const rejected =
      denied(params.slug, true) ??
      (menu.status === "DRAFT" ? null : rule("MENU_NOT_DRAFT")) ??
      invalidWindow(when);
    if (rejected) return rejected;
    Object.assign(menu, {
      serviceDate: when.serviceDate,
      startsAt: `${when.startsAt}:00`,
      endsAt: `${when.endsAt}:00`,
    });
    return HttpResponse.json(menu);
  }),
  http.post(`${MENUS}/:id/items`, async ({ params, request }) => {
    const { itemId, price, quantity } = (await request.json()) as AddMenuItemRequest;
    return changeMenu(params, (menu) => {
      const item = catalog.find(({ id }) => id === itemId);
      if (!item) return notFound("ITEM_NOT_FOUND");
      if (item.archived) return rule("ITEM_ARCHIVED");
      if (menu.items.some((offered) => offered.itemId === itemId)) {
        return rule("ITEM_ALREADY_IN_MENU");
      }
      // The catalog's price is only a suggestion, used when the request sets none
      menu.items.push({
        itemId,
        name: item.name,
        price: price ?? item.suggestedPrice,
        quantity,
        archived: false,
      });
      menu.items.sort((a, b) => a.name.localeCompare(b.name));
    });
  }),
  http.put(`${MENUS}/:id/items/:itemId`, async ({ params, request }) => {
    const change = (await request.json()) as UpdateMenuItemRequest;
    return changeMenu(params, (menu) => {
      const item = menu.items.find(({ itemId }) => itemId === Number(params.itemId));
      if (!item) return notFound("MENU_ITEM_NOT_FOUND");
      Object.assign(item, change);
    });
  }),
  http.delete(`${MENUS}/:id/items/:itemId`, ({ params }) =>
    changeMenu(params, (menu) => {
      menu.items = menu.items.filter(({ itemId }) => itemId !== Number(params.itemId));
    })
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
