import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import type { BusinessDetail, BusinessSummary, MenuDetail } from "@/api/types";

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

const notFound = (code: string) =>
  HttpResponse.json(
    { status: 404, title: "Resource not found", detail: "Not found", code },
    { status: 404, headers: { "Content-Type": "application/problem+json" } }
  );

export const handlers = [
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
