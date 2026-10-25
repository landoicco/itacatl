// Mirrors the backend's DTOs (docs/domain-model.md#public-read-api and #management-api).
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

// Accounts (docs/security.md)

export type MembershipRole = "OWNER" | "STAFF";

export interface Membership {
  slug: string;
  name: string;
  role: MembershipRole;
}

export interface CurrentUser {
  email: string;
  name: string;
  /** Sorted by name */
  businesses: Membership[];
}

export interface SignInRequest {
  email: string;
  password: string;
}

// Management API: everything below is scoped to one business

export interface CatalogItem {
  id: number;
  name: string;
  description: string | null;
  suggestedPrice: number;
  alcoholic: boolean;
  archived: boolean;
}

export interface ItemRequest {
  name: string;
  description: string | null;
  suggestedPrice: number;
  alcoholic: boolean;
}

export type MenuStatus = "DRAFT" | "PUBLISHED";

export interface ManagedMenuItem {
  /** The catalog item, which is also how the menu's endpoints address it */
  itemId: number;
  name: string;
  price: number;
  quantity: number;
  /** Archived in the catalog after it was added */
  archived: boolean;
}

export interface ManagedMenu {
  id: number;
  serviceDate: string;
  startsAt: string;
  endsAt: string;
  status: MenuStatus;
  /** Sorted by name */
  items: ManagedMenuItem[];
}

/** When a menu is served: a date and a window in the business's local time */
export interface MenuWindow {
  serviceDate: string;
  startsAt: string;
  endsAt: string;
}

export interface AddMenuItemRequest {
  itemId: number;
  /** The item's suggested price when omitted */
  price?: number;
  quantity: number;
}

export interface UpdateMenuItemRequest {
  price: number;
  quantity: number;
}

/** One invalid field of a VALIDATION_FAILED error; `code` is the constraint, e.g. "NotBlank" */
export interface FieldError {
  field: string;
  code: string;
}
