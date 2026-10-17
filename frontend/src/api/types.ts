// Mirrors the backend's DTOs (docs/domain-model.md#public-read-api).
// Dates and times are local to the business, without an offset: "2026-10-17T12:00:00".

export type DayOfWeek =
  "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export interface OpeningStatus {
  state: "OPEN" | "CLOSED";
  /** When the current window ends, if open */
  closesAt: string | null;
  /** When the next window starts, if closed and one exists in the next two weeks */
  opensAt: string | null;
}

interface BusinessBase {
  slug: string;
  name: string;
  description: string | null;
  timeZone: string;
  openingStatus: OpeningStatus;
}

export interface BusinessSummary extends BusinessBase {
  /** Item names of the current or next menu, sorted; empty when there is none */
  featuredItems: string[];
}

export interface BusinessHours {
  dayOfWeek: DayOfWeek;
  opensAt: string;
  closesAt: string;
}

export interface MenuSummary {
  id: number;
  serviceDate: string;
  startsAt: string;
  endsAt: string;
}

export interface BusinessDetail extends BusinessBase {
  acceptsPickup: boolean;
  acceptsDineIn: boolean;
  weeklyHours: BusinessHours[];
  upcomingMenus: MenuSummary[];
}

export interface MenuItem {
  id: number;
  name: string;
  description: string | null;
  price: number;
  available: number;
  alcoholic: boolean;
}

export interface MenuDetail {
  id: number;
  businessSlug: string;
  businessName: string;
  serviceDate: string;
  startsAt: string;
  endsAt: string;
  items: MenuItem[];
}
