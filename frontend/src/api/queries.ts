import { useQuery } from "@tanstack/react-query";
import { api } from "./client";

// One place for cache keys, so invalidation cannot miss a variant. Public data lives under
// "businesses" and "menus"; the management API under "manage", so signing out drops all of it
export const queryKeys = {
  businesses: ["businesses"] as const,
  business: (slug: string) => ["businesses", slug] as const,
  menu: (id: number) => ["menus", id] as const,

  currentUser: ["currentUser"] as const,
  manage: ["manage"] as const,
  catalog: (slug: string) => ["manage", slug, "items"] as const,
  managedMenus: (slug: string) => ["manage", slug, "menus", "list"] as const,
  managedMenu: (slug: string, id: number) => ["manage", slug, "menus", id] as const,
};

// Opening status depends on the time of day, so lists refresh while the page stays open
const OPENING_STATUS_REFRESH_MS = 60_000;

export function useBusinesses() {
  return useQuery({
    queryKey: queryKeys.businesses,
    queryFn: api.getBusinesses,
    refetchInterval: OPENING_STATUS_REFRESH_MS,
  });
}

export function useBusiness(slug: string) {
  return useQuery({
    queryKey: queryKeys.business(slug),
    queryFn: () => api.getBusiness(slug),
    refetchInterval: OPENING_STATUS_REFRESH_MS,
  });
}

/** A menu's items; waits until a menu is selected. */
export function useMenu(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.menu(id ?? 0),
    queryFn: () => api.getMenu(id!),
    enabled: id !== undefined,
  });
}
