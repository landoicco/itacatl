import { useQuery } from "@tanstack/react-query";
import { api } from "./client";

// One place for cache keys, so invalidation later (slice 2) cannot miss a variant
export const queryKeys = {
  businesses: ["businesses"] as const,
  business: (slug: string) => ["businesses", slug] as const,
  menu: (id: number) => ["menus", id] as const,
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
